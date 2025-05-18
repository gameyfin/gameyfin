package de.grimsi.gameyfin.libraries

import de.grimsi.gameyfin.core.filesystem.FilesystemService
import de.grimsi.gameyfin.games.GameService
import de.grimsi.gameyfin.games.dto.GameDto
import de.grimsi.gameyfin.games.entities.Game
import de.grimsi.gameyfin.games.toDto
import de.grimsi.gameyfin.libraries.dto.DirectoryMappingDto
import de.grimsi.gameyfin.libraries.dto.LibraryDto
import de.grimsi.gameyfin.libraries.dto.LibraryStatsDto
import de.grimsi.gameyfin.libraries.dto.LibraryUpdateDto
import de.grimsi.gameyfin.libraries.enums.ScanType
import de.grimsi.gameyfin.media.ImageService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.measureTimedValue

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
    }

    /**
     * Creates or updates a library in the repository.
     *
     * @param library: The library to create or update.
     * @return The created or updated LibraryDto object.
     */
    fun create(library: LibraryDto): LibraryDto {
        val entity = libraryRepository.save(toEntity(library))
        return toDto(entity)
    }

    /**
     * Updates a library entity with the non-null fields from a LibraryUpdateDto.
     *
     * @param libraryDto: The LibraryUpdateDto containing the fields to update.
     * @return The updated LibraryDto.
     * @throws IllegalArgumentException if the library ID is null or the library is not found.
     */
    fun update(libraryDto: LibraryUpdateDto): LibraryDto {
        val existingLibrary = libraryRepository.findByIdOrNull(libraryDto.id)
            ?: throw IllegalArgumentException("Library with ID $libraryDto.id not found")

        // Update only non-null fields
        libraryDto.name?.let { existingLibrary.name = it }
        libraryDto.directories?.let {
            existingLibrary.directories = it
                .map { d -> DirectoryMapping(internalPath = d.internalPath, externalPath = d.externalPath) }
                .toMutableList()
        }

        val updatedLibrary = libraryRepository.save(existingLibrary)
        return toDto(updatedLibrary)
    }

    /**
     * Retrieves all libraries from the repository.
     */
    fun getAllLibraries(): Collection<LibraryDto> {
        val entities = libraryRepository.findAll()
        return entities.map { toDto(it) }
    }

    /**
     * Deletes a library from the repository.
     *
     * @param libraryId: ID of the library to delete.
     */
    fun deleteLibrary(libraryId: Long) {
        libraryRepository.deleteById(libraryId)
    }

    /**
     * Deletes all libraries from the repository.
     */
    fun deleteAllLibraries() {
        libraryRepository.deleteAll()
    }

    /**
     * Retrieves all games in a library.
     *
     * @param libraryId: The ID of the library to retrieve games from.
     * @return A collection of GameDto objects representing the games in the library.
     */
    fun getGamesInLibrary(libraryId: Long): Collection<GameDto> {
        val library = libraryRepository.findByIdOrNull(libraryId)
            ?: throw IllegalArgumentException("Library with ID $libraryId not found")

        val games = library.games.map { it.toDto() }

        return games
    }

    /**
     * Adds a game to the library.
     *
     * @param game: The game to add.
     * @param library: The library to add the game to.
     * @return The updated library.
     */
    fun addGameToLibrary(game: Game, library: Library): Library {
        if (library.games.any { it.id == game.id }) return library

        library.games.add(game)
        return libraryRepository.save(library)
    }

    /**
     * Adds a collection of games to the library.
     *
     * @param games: The collection of games to add.
     * @param library: The library to add the games to.
     * @return The updated library.
     */
    fun addGamesToLibrary(games: Collection<Game>, library: Library): Library {
        val newGames = games.filter { game -> library.games.none { it.id == game.id } }
        library.games.addAll(newGames)
        return library
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
     * Converts a Library entity to a LibraryDto.
     *
     * @param library: The Library entity to convert.
     * @return The converted LibraryDto.
     */
    private fun toDto(library: Library): LibraryDto {
        val libraryId = library.id ?: throw IllegalArgumentException("Library ID is null")

        val statsDto = LibraryStatsDto(
            gamesCount = library.games.size,
            downloadedGamesCount = library.games.sumOf { it.downloadCount }
        )

        return LibraryDto(
            id = libraryId,
            name = library.name,
            directories = library.directories.map { DirectoryMappingDto(it.internalPath, it.externalPath) },
            stats = statsDto
        )
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