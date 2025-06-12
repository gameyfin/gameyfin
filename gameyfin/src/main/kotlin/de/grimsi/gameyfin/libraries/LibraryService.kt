package de.grimsi.gameyfin.libraries

import de.grimsi.gameyfin.core.filesystem.FilesystemService
import de.grimsi.gameyfin.games.GameService
import de.grimsi.gameyfin.games.entities.Game
import de.grimsi.gameyfin.libraries.dto.*
import de.grimsi.gameyfin.libraries.enums.ScanType
import de.grimsi.gameyfin.media.ImageService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@Service
class LibraryService(
    private val libraryRepository: LibraryRepository,
    private val filesystemService: FilesystemService,
    private val gameService: GameService,
    private val imageService: ImageService
) {

    companion object {
        private val log = KotlinLogging.logger {}
        private val executor = Executors.newVirtualThreadPerTaskExecutor()
        private val SCAN_RESULT_TTL = 24.hours.toJavaDuration()

        /* Websockets */
        private val libraryEvents = Sinks.many().multicast().onBackpressureBuffer<LibraryEvent>(1024, false)
        private val scanProgressEvents = Sinks.many().replay().limit<LibraryScanProgress>(SCAN_RESULT_TTL)

        fun subscribeToLibraryEvents(): Flux<List<LibraryEvent>> {
            log.debug { "New subscription for libraryEvents" }
            return libraryEvents.asFlux()
                .buffer(100.milliseconds.toJavaDuration())
                .doOnSubscribe {
                    log.debug { "Subscriber added to libraryEvents [${libraryEvents.currentSubscriberCount()}]" }
                }
                .doFinally {
                    log.debug { "Subscriber removed from libraryEvents with signal type $it [${libraryEvents.currentSubscriberCount()}]" }
                }
        }

        fun subscribeToScanProgressEvents(): Flux<List<LibraryScanProgress>> {
            log.debug { "New subscription for scanProgressEvents" }
            return scanProgressEvents.asFlux()
                .buffer(1.seconds.toJavaDuration())
                .doOnSubscribe {
                    log.debug { "Subscriber added to scanProgressEvents [${scanProgressEvents.currentSubscriberCount()}]" }
                }
                .doFinally {
                    log.debug { "Subscriber removed from scanProgressEvents with signal type $it [${scanProgressEvents.currentSubscriberCount()}]" }
                }
        }

        fun emit(event: LibraryEvent) {
            libraryEvents.tryEmitNext(event)
        }

        fun emit(scanProgressDto: LibraryScanProgress) {
            scanProgressEvents.tryEmitNext(scanProgressDto)
        }
    }


    /**
     * Retrieves all libraries from the repository.
     */
    fun getAll(): List<LibraryDto> {
        val entities = libraryRepository.findAll()
        return entities.map { it.toDto() }
    }

    /**
     * Creates or updates a library in the repository.
     *
     * @param library: The library to create or update.
     * @return The created or updated LibraryDto object.
     */
    fun create(library: LibraryDto, scanAfterCreation: Boolean) {
        val newLibrary = libraryRepository.save(toEntity(library))

        if (scanAfterCreation) {
            triggerScanSingleLibrary(ScanType.QUICK, newLibrary)
        }
    }

    /**
     * Updates a library entity with the non-null fields from a LibraryUpdateDto.
     *
     * @param libraryUpdateDto: The LibraryUpdateDto containing the fields to update.
     * @return The updated LibraryDto.
     * @throws IllegalArgumentException if the library ID is null or the library is not found.
     */
    fun update(libraryUpdateDto: LibraryUpdateDto) {
        var library = libraryRepository.findByIdOrNull(libraryUpdateDto.id)
            ?: throw IllegalArgumentException("Library with ID $libraryUpdateDto.id not found")

        // Update only non-null fields
        libraryUpdateDto.name?.let { library.name = it }
        libraryUpdateDto.directories?.let {
            library.directories.clear()
            library.directories.addAll(
                it.map { d -> DirectoryMapping(internalPath = d.internalPath, externalPath = d.externalPath) }
            )
        }
        libraryUpdateDto.unmatchedPaths?.let {
            library.unmatchedPaths.clear()
            library.unmatchedPaths.addAll(it)
        }

        library.updatedAt = Instant.now() // Force the EntityListener to trigger an update and update the timestamp
        libraryRepository.save(library)
    }

    /**
     * Deletes a library from the repository.
     *
     * @param libraryId: ID of the library to delete.
     */
    fun delete(libraryId: Long) {
        libraryRepository.deleteById(libraryId)
    }

    fun deleteGameFromLibrary(gameId: Long) {
        val game = gameService.getById(gameId)
        var library = game.library

        library.games.removeIf { it.id == gameId }
        library.unmatchedPaths.add(game.metadata.path)
        
        library.updatedAt = Instant.now() // Force the EntityListener to trigger an update and update the timestamp
        libraryRepository.save(library)
    }

    /**
     * Wrapper function to trigger a scan for a list of libraries.
     */
    fun triggerScan(scanType: ScanType, libraryDtos: Collection<LibraryDto>?) {
        executor.submit {
            when (scanType) {
                ScanType.QUICK -> quickScan(libraryDtos)
                ScanType.FULL -> TODO()
            }
        }
    }

    fun triggerScanSingleLibrary(scanType: ScanType, library: Library) {
        triggerScan(scanType, listOf(library.toDto()))
    }

    /**
     * Triggers a quick scan for a list of libraries.
     * A quick scan will only scan for new games and deleted games, but will not touch existing games.
     * If no list is provided, all libraries will be scanned.
     *
     * @param libraryDtos: List of LibraryDto objects to scan.
     */
    fun quickScan(libraryDtos: Collection<LibraryDto>?) {
        val libraries = libraryDtos?.map { toEntity(it) } ?: libraryRepository.findAll()
        libraries.forEach { executor.submit { quickScan(it) } }
    }

    fun quickScan(library: Library) {

        val progress = LibraryScanProgress(
            libraryId = library.id!!,
            currentStep = LibraryScanStep(
                description = "Scanning filesystem"
            )
        )
        emit(progress)

        val scanResult = filesystemService.scanLibraryForGamefiles(library)
        val gamePaths = scanResult.newPaths
        val removedGamePaths = scanResult.removedGamePaths.map { it.toString() }
        val removedUnmatchedPaths = scanResult.removedUnmatchedPaths.map { it.toString() }

        val totalPaths = gamePaths.size
        val completedMetadata = AtomicInteger(0)
        val completedImageDownload = AtomicInteger(0)
        val calculatedFileSize = AtomicInteger(0)

        progress.currentStep = LibraryScanStep(
            description = "Matching games",
            current = 0,
            total = totalPaths
        )
        emit(progress)

        // 1. Fetch metadata for each game
        val newUnmatchedPaths = ConcurrentHashMap.newKeySet<String>()

        val metadataTasks = gamePaths.map { path ->
            Callable<Game?> {
                try {
                    val game = gameService.matchFromFile(path, library)

                    if (game == null) {
                        newUnmatchedPaths.add(path.toString())
                        return@Callable null
                    }

                    progress.currentStep.current = completedMetadata.incrementAndGet()
                    emit(progress)

                    return@Callable game
                } catch (e: Exception) {
                    log.error(e) { "Error processing game: ${e.message}" }
                    newUnmatchedPaths.add(path.toString())

                    return@Callable null
                }
            }
        }

        // 1.1 Wait for all metadata tasks to complete
        val matchedGames = executor.invokeAll(metadataTasks).mapNotNull { it.get() }

        // 1.2 Add unmatched paths to the library
        library.unmatchedPaths.removeAll(removedUnmatchedPaths)
        library.unmatchedPaths.addAll(newUnmatchedPaths)

        // 1.3 Remove deleted games from the library
        val removedGames = library.games.filter { removedGamePaths.contains(it.metadata.path) }
        library.games.removeAll(removedGames)

        // 2. Download all images
        val totalImages = matchedGames.count { it.coverImage != null } + matchedGames.sumOf { it.images.size }

        progress.currentStep = LibraryScanStep(
            description = "Downloading images",
            current = 0,
            total = totalImages
        )
        emit(progress)

        val imageDownloadTasks = matchedGames.map { game ->
            Callable<Game?> {
                try {
                    game.coverImage?.let {
                        imageService.downloadIfNew(it)
                        completedImageDownload.andIncrement
                    }

                    game.images.map {
                        imageService.downloadIfNew(it)
                        completedImageDownload.andIncrement
                    }

                    progress.currentStep.current = completedImageDownload.get()
                    emit(progress)

                    game
                } catch (e: Exception) {
                    log.error(e) { "Error downloading images for game: ${e.message}" }
                    null
                }
            }
        }

        val gamesWithImages = executor.invokeAll(imageDownloadTasks).mapNotNull { it.get() }


        // 3. Calculate game file sizes
        progress.currentStep = LibraryScanStep(
            description = "Calculating file sizes",
            current = 0,
            total = gamesWithImages.size
        )
        emit(progress)

        val calculateFileSizeTask = gamesWithImages.map { game ->
            Callable {
                game.metadata.path.let { path ->
                    val fileSize = filesystemService.calculateFileSize(path)
                    game.metadata.fileSize = fileSize

                    progress.currentStep.current = calculatedFileSize.incrementAndGet()
                    emit(progress)

                    game
                }
            }
        }

        val gamesWithFileSizes = executor.invokeAll(calculateFileSizeTask).map { it.get() }

        progress.currentStep = LibraryScanStep(
            description = "Finishing up",
            current = 0,
            total = gamesWithFileSizes.size
        )
        emit(progress)

        // 4. Persist new games
        val persistedGames = gameService.create(gamesWithFileSizes)

        progress.currentStep.current = persistedGames.size
        emit(progress)

        // 5. Add new games to library
        addGamesToLibrary(persistedGames, library)

        // 6. Persist library
        library.updatedAt = Instant.now() // Force the EntityListener to trigger an update and update the timestamp
        libraryRepository.save(library)

        progress.currentStep = LibraryScanStep(description = "Finished")
        progress.finishedAt = java.time.Instant.now()
        progress.status = LibraryScanStatus.COMPLETED
        progress.result = LibraryScanResult(
            new = persistedGames.size,
            removed = removedGames.size,
            unmatched = removedUnmatchedPaths.size
        )
        emit(progress)
    }

    /**
     * Adds a collection of games to the library.
     *
     * @param games: The collection of games to add.
     * @param library: The library to add the games to.
     * @return The updated library.
     */
    private fun addGamesToLibrary(games: Collection<Game>, library: Library): Library {
        val newGames = games.filter { game -> library.games.none { it.id == game.id } }
        library.games.addAll(newGames)
        return library
    }

    /**
     * Converts a LibraryDto to a Library entity.
     *
     * @param library: The LibraryDto to convert.
     * @return The converted Library entity.
     */
    private fun toEntity(library: LibraryDto): Library {
        return libraryRepository.findByIdOrNull(library.id) ?: Library(
            name = library.name,
            directories = library.directories.map {
                DirectoryMapping(internalPath = it.internalPath, externalPath = it.externalPath)
            }.toMutableList(),
        )
    }
}

fun Library.toDto(): LibraryDto {
    val statsDto = LibraryStatsDto(
        gamesCount = this.games.size,
        downloadedGamesCount = this.games.sumOf { it.metadata.downloadCount }
    )

    return LibraryDto(
        id = this.id!!,
        name = this.name,
        directories = this.directories.map { DirectoryMappingDto(it.internalPath, it.externalPath) },
        games = this.games.mapNotNull { it.id },
        stats = statsDto,
        unmatchedPaths = this.unmatchedPaths
    )
}