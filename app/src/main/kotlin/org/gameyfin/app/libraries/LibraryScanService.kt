package org.gameyfin.app.libraries

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.filesystem.FilesystemService
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.Image
import org.gameyfin.app.libraries.dto.*
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.libraries.enums.ScanType
import org.gameyfin.app.libraries.scan.*
import org.gameyfin.app.media.ImageService
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@Service
class LibraryScanService(
    private val libraryRepository: LibraryRepository,
    private val filesystemService: FilesystemService,
    private val libraryCoreService: LibraryCoreService,
    private val gameService: GameService,
    private val imageService: ImageService,
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private val SCAN_RESULT_TTL = 24.hours.toJavaDuration()
        private val scanProgressEvents = Sinks.many().replay().limit<LibraryScanProgress>(SCAN_RESULT_TTL)

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

        fun emit(scanProgressDto: LibraryScanProgress) {
            scanProgressEvents.tryEmitNext(scanProgressDto)
        }

        private val executor = Executors.newVirtualThreadPerTaskExecutor()
        private val scansInProgress = ConcurrentHashMap<Long, Boolean>()
    }

    /**
     * Wrapper function to trigger a scan for a list of libraries.
     */
    fun triggerScan(scanType: ScanType, libraryIds: Collection<Long>?) {
        val libraries = libraryIds?.let { libraryRepository.findAllById(libraryIds) } ?: libraryRepository.findAll()
        libraries.forEach { library ->
            val libraryId = library.id!!
            if (scansInProgress.putIfAbsent(libraryId, true) == null) {
                executor.submit {
                    try {
                        when (scanType) {
                            ScanType.QUICK -> quickScan(library)
                            ScanType.FULL -> fullScan(library, false)
                            ScanType.SCHEDULED -> fullScan(library, true)
                        }
                    } finally {
                        scansInProgress.remove(libraryId)
                    }
                }
            } else {
                log.info { "Scan already in progress for library $libraryId, skipping." }
            }
        }
    }

    private fun quickScan(library: Library) {
        val progress = LibraryScanProgress(
            libraryId = library.id!!,
            type = ScanType.QUICK,
            currentStep = LibraryScanStep(
                description = "Scanning filesystem"
            )
        )
        emit(progress)

        try {
            val scanResult = filesystemService.scanLibraryForGamefiles(library)
            val newPaths = scanResult.newPaths
            val removedGamePaths = scanResult.removedGamePaths.map { it.toString() }
            val removedUnmatchedPaths = scanResult.removedUnmatchedPaths.map { it.toString() }

            progress.currentStep = LibraryScanStep(
                description = "Matching new games",
                current = 0,
                total = newPaths.size
            )
            emit(progress)

            // 1. Match new games
            val (newUnmatchedPaths, matchedGames) = matchNewGames(library, newPaths, progress)

            val (removedGames) = updateLibrary(
                library,
                removedUnmatchedPaths,
                newUnmatchedPaths,
                removedGamePaths
            )

            // 2. Download all images
            val totalImages = matchedGames.count { it.coverImage != null } +
                    matchedGames.count { it.headerImage !== null } +
                    matchedGames.sumOf { it.images.size }

            progress.currentStep = LibraryScanStep(
                description = "Downloading images",
                current = 0,
                total = totalImages
            )
            emit(progress)

            val (gamesWithImages) = downloadImages(matchedGames, progress)

            // 3. Calculate game file sizes
            progress.currentStep = LibraryScanStep(
                description = "Calculating file sizes",
                current = 0,
                total = gamesWithImages.size
            )
            emit(progress)

            val (gamesWithFileSizes) = calculateFileSizes(gamesWithImages, progress)

            progress.currentStep = LibraryScanStep(
                description = "Finishing up",
                current = 0,
                total = gamesWithFileSizes.size
            )
            emit(progress)

            val (persistedGames) = finishScan(gamesWithFileSizes, library, progress)

            progress.currentStep = LibraryScanStep(description = "Finished")
            progress.finishedAt = Instant.now()
            progress.status = LibraryScanStatus.COMPLETED
            progress.result = QuickScanResult(
                new = persistedGames.size,
                removed = removedGames.size,
                unmatched = newUnmatchedPaths.size
            )
            emit(progress)
        } catch (e: Exception) {
            log.error { "Error during quick scan for library ${library.id}: ${e.message}" }
            log.debug(e) {}
            progress.status = LibraryScanStatus.FAILED
            progress.finishedAt = Instant.now()
            emit(progress)
        }
    }

    private fun fullScan(library: Library, triggeredBySchedule: Boolean) {
        val progress = LibraryScanProgress(
            libraryId = library.id!!,
            type = if (triggeredBySchedule) ScanType.SCHEDULED else ScanType.FULL,
            currentStep = LibraryScanStep(
                description = "Scanning filesystem"
            )
        )
        emit(progress)

        try {
            val scanResult = filesystemService.scanLibraryForGamefiles(library)
            val newPaths = scanResult.newPaths
            val removedGamePaths = scanResult.removedGamePaths.map { it.toString() }
            val removedUnmatchedPaths = scanResult.removedUnmatchedPaths.map { it.toString() }


            // 1. Update existing games
            progress.currentStep = LibraryScanStep(
                description = "Updating existing games",
                current = 0,
                total = library.games.size
            )
            emit(progress)

            val (updatedGames) = updateExistingGames(library.games, progress)

            // 2. Match new games
            progress.currentStep = LibraryScanStep(
                description = "Matching new games",
                current = 0,
                total = newPaths.size
            )
            emit(progress)

            val (newUnmatchedPaths, newMatchedGames) = matchNewGames(library, newPaths, progress)

            val (removedGames) = updateLibrary(
                library,
                removedUnmatchedPaths,
                newUnmatchedPaths,
                removedGamePaths
            )

            // 3. Download all images
            val newAndUpdatedGames = newMatchedGames + updatedGames

            val totalImages = newAndUpdatedGames.count { it.coverImage != null } +
                    newAndUpdatedGames.count { it.headerImage !== null } +
                    newAndUpdatedGames.sumOf { it.images.size }

            progress.currentStep = LibraryScanStep(
                description = "Downloading images",
                current = 0,
                total = totalImages
            )
            emit(progress)

            val (gamesWithImages) = downloadImages(newAndUpdatedGames, progress)

            // 4. Calculate game file sizes
            progress.currentStep = LibraryScanStep(
                description = "Calculating file sizes",
                current = 0,
                total = gamesWithImages.size
            )
            emit(progress)

            val (gamesWithFileSizes) = calculateFileSizes(gamesWithImages, progress)

            // 5. Finish scan
            progress.currentStep = LibraryScanStep(
                description = "Finishing up",
                current = 0,
                total = gamesWithFileSizes.size
            )
            emit(progress)

            val (persistedGames) = finishScan(gamesWithFileSizes, library, progress)

            // 6. Send final progress update
            progress.currentStep = LibraryScanStep(description = "Finished")
            progress.finishedAt = Instant.now()
            progress.status = LibraryScanStatus.COMPLETED
            progress.result = FullScanResult(
                new = persistedGames.size,
                removed = removedGames.size,
                unmatched = newUnmatchedPaths.size,
                updated = updatedGames.size
            )
            emit(progress)
        } catch (e: Exception) {
            log.error { "Error during full scan for library ${library.id}: ${e.message}" }
            log.debug(e) {}
            progress.status = LibraryScanStatus.FAILED
            progress.finishedAt = Instant.now()
            emit(progress)
            return
        }
    }

    private fun matchNewGames(
        library: Library,
        gamePaths: List<Path>,
        progress: LibraryScanProgress
    ): MatchNewGamesResult {
        val completedMetadata = AtomicInteger(0)

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

                    return@Callable game
                } catch (e: Exception) {
                    log.error { "Error processing game: ${e.message}" }
                    log.debug(e) {}
                    newUnmatchedPaths.add(path.toString())

                    return@Callable null
                } finally {
                    progress.currentStep.current = completedMetadata.incrementAndGet()
                    emit(progress)
                }
            }
        }

        // 1.1 Wait for all metadata tasks to complete
        val matchedGames = executor.invokeAll(metadataTasks).mapNotNull { it.get() }

        return MatchNewGamesResult(
            unmatchedPaths = newUnmatchedPaths.toList(),
            matchedGames = matchedGames
        )
    }

    private fun updateLibrary(
        library: Library,
        removedUnmatchedPaths: List<String>,
        newUnmatchedPaths: List<String>,
        removedGamePaths: List<String>
    ): UpdateLibraryResult {
        // 1.2 Add unmatched paths to the library
        library.unmatchedPaths.removeAll(removedUnmatchedPaths)
        library.unmatchedPaths.addAll(newUnmatchedPaths)

        // 1.3 Remove deleted games from the library
        val removedGames = library.games.filter { removedGamePaths.contains(it.metadata.path) }
        library.games.removeAll(removedGames)

        return UpdateLibraryResult(removedGames = removedGames)
    }

    private fun downloadImages(games: List<Game>, progress: LibraryScanProgress): DownloadImagesResult {
        val completedImageDownload = AtomicInteger(0)

        // Collect all images from all games in the batch
        val allImages = games.flatMap { game ->
            val images = mutableListOf<Image>()
            game.coverImage?.let { images.add(it) }
            game.headerImage?.let { images.add(it) }
            images.addAll(game.images)
            images
        }

        // Deduplicate by originalUrl
        val uniqueImages = allImages
            .filter { it.originalUrl != null }
            .distinctBy { it.originalUrl.toString() }

        // Download each unique image in parallel
        val imageDownloadTasks = uniqueImages.map { image ->
            Callable {
                try {
                    imageService.downloadIfNew(image)
                } catch (e: Exception) {
                    log.error { "Error downloading image '${image.originalUrl}': ${e.message}" }
                    log.debug(e) {}
                } finally {
                    progress.currentStep.current = completedImageDownload.incrementAndGet()
                    emit(progress)
                }
            }
        }
        executor.invokeAll(imageDownloadTasks)

        // For remaining duplicate images, just copy the content metadata from the downloaded unique image
        val uniqueImagesByUrl = uniqueImages.associateBy { it.originalUrl.toString() }

        allImages.filter { it.originalUrl != null && it !in uniqueImages }
            .forEach { duplicateImage ->
                val downloadedImage = uniqueImagesByUrl[duplicateImage.originalUrl.toString()]
                if (downloadedImage != null && downloadedImage.contentId != null) {
                    duplicateImage.contentId = downloadedImage.contentId
                    duplicateImage.contentLength = downloadedImage.contentLength
                    duplicateImage.mimeType = downloadedImage.mimeType
                }
                progress.currentStep.current = completedImageDownload.incrementAndGet()
                emit(progress)
            }

        return DownloadImagesResult(gamesWithImages = games)
    }

    private fun calculateFileSizes(games: List<Game>, progress: LibraryScanProgress): CalculateFilesizesResult {
        val calculatedFileSize = AtomicInteger(0)

        val calculateFileSizeTask = games.map { game ->
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

        return CalculateFilesizesResult(gamesWithFilesizes = gamesWithFileSizes)
    }

    private fun finishScan(games: List<Game>, library: Library, progress: LibraryScanProgress): FinishScanResult {
        // 4. Persist new games
        val persistedGames = gameService.create(games)

        progress.currentStep.current = persistedGames.size
        emit(progress)

        // 5. Add new games to library
        libraryCoreService.addGamesToLibrary(persistedGames, library)

        // 6. Persist library
        library.updatedAt = Instant.now() // Force the EntityListener to trigger an update and update the timestamp
        libraryRepository.save(library)

        return FinishScanResult(persistedGames = persistedGames)
    }

    private fun updateExistingGames(
        games: List<Game>,
        progress: LibraryScanProgress
    ): UpdateExistingGamesResult {
        val completedUpdates = AtomicInteger(0)

        val metadataTasks = games.map { game ->
            Callable<Game?> {
                try {
                    val game = gameService.update(game)
                    return@Callable game
                } catch (e: Exception) {
                    log.error { "Error updating game with id '${game.id}': ${e.message}" }
                    log.debug(e) {}
                    return@Callable null
                } finally {
                    progress.currentStep.current = completedUpdates.incrementAndGet()
                    emit(progress)
                }
            }
        }

        val updatedGames = executor.invokeAll(metadataTasks).mapNotNull { it.get() }
        return UpdateExistingGamesResult(updatedGames = updatedGames)
    }
}