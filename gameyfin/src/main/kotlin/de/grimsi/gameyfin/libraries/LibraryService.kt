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
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue
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

        /* Websockets */
        private val libraryEvents = Sinks.many().multicast().onBackpressureBuffer<LibraryEvent>(1024, false)

        fun subscribe(): Flux<List<LibraryEvent>> {
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

        fun emit(event: LibraryEvent) {
            libraryEvents.tryEmitNext(event)
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
    fun create(library: LibraryDto) {
        val entity = libraryRepository.save(toEntity(library))
    }

    /**
     * Updates a library entity with the non-null fields from a LibraryUpdateDto.
     *
     * @param libraryUpdateDto: The LibraryUpdateDto containing the fields to update.
     * @return The updated LibraryDto.
     * @throws IllegalArgumentException if the library ID is null or the library is not found.
     */
    fun update(libraryUpdateDto: LibraryUpdateDto) {
        val existingLibrary = libraryRepository.findByIdOrNull(libraryUpdateDto.id)
            ?: throw IllegalArgumentException("Library with ID $libraryUpdateDto.id not found")

        // Update only non-null fields
        libraryUpdateDto.name?.let { existingLibrary.name = it }
        libraryUpdateDto.directories?.let {
            existingLibrary.directories.clear()
            existingLibrary.directories.addAll(
                it.map { d -> DirectoryMapping(internalPath = d.internalPath, externalPath = d.externalPath) }
            )
        }

        libraryRepository.save(existingLibrary)
    }

    /**
     * Deletes a library from the repository.
     *
     * @param libraryId: ID of the library to delete.
     */
    fun delete(libraryId: Long) {
        libraryRepository.deleteById(libraryId)
    }

    /**
     * Wrapper function to trigger a scan for a list of libraries.
     */
    fun triggerScan(scanType: ScanType, libraryDtos: Collection<LibraryDto>?) {
        val scanResult = measureTimedValue {
            when (scanType) {
                ScanType.QUICK -> quickScan(libraryDtos)
                ScanType.FULL -> TODO()
            }
        }

        log.info {
            """
            Scan completed in ${scanResult.duration}.
            Libraries scanned: ${libraryDtos?.joinToString { it.name } ?: "all libraries"}
            Scan type: ${scanType.toString().lowercase()}
            New games added: ${scanResult.value.newGames.size}
            Removed games: ${scanResult.value.removedGames.size}
            New unmatched paths: ${scanResult.value.newUnmatchedPaths.size}
        """.trimIndent()
        }
    }

    /**
     * Triggers a quick scan for a list of libraries.
     * A quick scan will only scan for new games and deleted games, but will not touch existing games.
     * If no list is provided, all libraries will be scanned.
     *
     * @param libraryDtos: List of LibraryDto objects to scan.
     */
    fun quickScan(libraryDtos: Collection<LibraryDto>?): LibraryScanResult {
        val libraries = libraryDtos?.map { toEntity(it) } ?: libraryRepository.findAll()

        val scanResults: List<LibraryScanResult> = libraries.map { library ->
            val scanResult = filesystemService.scanLibraryForGamefiles(library)
            val gamePaths = scanResult.newPaths
            val removedGamePaths = scanResult.removedGamePaths.map { it.toString() }
            val removedUnmatchedPaths = scanResult.removedUnmatchedPaths.map { it.toString() }

            val totalPaths = gamePaths.size
            val completedMetadata = AtomicInteger(0)
            val completedImageDownload = AtomicInteger(0)
            val calculatedFileSize = AtomicInteger(0)

            log.info { "Scanning library '${library.name}' with $totalPaths paths..." }

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

                        val progress = completedMetadata.incrementAndGet()
                        log.debug { "${progress}/${totalPaths} metadata matched" }

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
            val removedGames = gameService.getAllByPaths(removedGamePaths)
            library.games.removeAll(removedGames)

            // 2. Download all images
            val totalImages = matchedGames.count { it.coverImage != null } + matchedGames.sumOf { it.images.size }

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

                        log.debug { "${completedImageDownload}/${totalImages} images downloaded" }

                        game
                    } catch (e: Exception) {
                        log.error(e) { "Error downloading images for game: ${e.message}" }
                        null
                    }
                }
            }

            val gamesWithImages = executor.invokeAll(imageDownloadTasks).mapNotNull { it.get() }

            // 3. Calculate game file sizes
            val calculateFileSizeTask = matchedGames.map { game ->
                Callable {
                    game.path.let { path ->
                        val fileSize = filesystemService.calculateFileSize(path)
                        game.fileSize = fileSize
                        val progress = calculatedFileSize.incrementAndGet()
                        log.debug { "${progress}/${totalPaths} file sizes calculated" }
                        game
                    }
                }
            }

            val gamesWithFileSizes = executor.invokeAll(calculateFileSizeTask).map { it.get() }


            // 4. Persist new games
            val persistedGames = gameService.create(gamesWithImages)
            log.debug { "${persistedGames.size}/${totalPaths} saved to database" }

            // 5. Add new games to library
            addGamesToLibrary(persistedGames, library)

            // 6. Persist library
            libraryRepository.save(library)

            return LibraryScanResult(
                libraries = listOf(library),
                newGames = persistedGames,
                removedGames = removedGames,
                newUnmatchedPaths = newUnmatchedPaths.toList()
            )
        }

        return scanResults.reduce { acc, scanResult ->
            LibraryScanResult(
                libraries = acc.libraries + scanResult.libraries,
                newGames = acc.newGames + scanResult.newGames,
                removedGames = acc.removedGames + scanResult.removedGames,
                newUnmatchedPaths = acc.newUnmatchedPaths + scanResult.newUnmatchedPaths
            )
        }
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
            }.toMutableList()
        )
    }
}

fun Library.toDto(): LibraryDto {
    val libraryId = this.id ?: throw IllegalArgumentException("Library ID is null")

    val statsDto = LibraryStatsDto(
        gamesCount = this.games.size,
        downloadedGamesCount = this.games.sumOf { it.downloadCount }
    )

    return LibraryDto(
        id = libraryId,
        name = this.name,
        directories = this.directories.map { DirectoryMappingDto(it.internalPath, it.externalPath) },
        games = this.games.mapNotNull { it.id },
        stats = statsDto
    )
}