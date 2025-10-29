package org.gameyfin.app.libraries

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.filesystem.FilesystemService
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.dto.*
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.libraries.enums.ScanType
import org.gameyfin.app.libraries.scan.LibraryGameProcessor
import org.gameyfin.app.libraries.scan.MatchNewGamesResult
import org.gameyfin.app.libraries.scan.UpdateExistingGamesResult
import org.gameyfin.app.libraries.scan.UpdateLibraryResult
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
    private val libraryGameProcessor: LibraryGameProcessor,
    private val gameRepository: GameRepository,
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

        private val executor = Executors.newFixedThreadPool(16)
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
                description = "Processing new games",
                current = 0,
                total = newPaths.size
            )
            emit(progress)

            // 1. Process each new game independently
            val (newUnmatchedPaths, persistedNewGames) = processNewGames(library, newPaths, progress)

            // 2. Update library (removed games/unmatched, and add persisted new ones)
            val (removedGames) = updateLibrary(
                library,
                removedUnmatchedPaths,
                newUnmatchedPaths,
                removedGamePaths
            )

            // 3. Finish scan: persist library changes and report
            progress.currentStep = LibraryScanStep(
                description = "Finishing up",
                current = 0,
                total = persistedNewGames.size
            )
            emit(progress)

            finishScanPersisted(persistedNewGames, library, progress)

            progress.currentStep = LibraryScanStep(description = "Finished")
            progress.finishedAt = Instant.now()
            progress.status = LibraryScanStatus.COMPLETED
            progress.result = QuickScanResult(
                new = persistedNewGames.size,
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


            // 1. Update existing games (individually)
            progress.currentStep = LibraryScanStep(
                description = "Updating existing games",
                current = 0,
                total = library.games.size
            )
            emit(progress)

            val (updatedGames) = updateExistingGames(library.games, progress)

            // 2. Process new games (individually)
            progress.currentStep = LibraryScanStep(
                description = "Processing new games",
                current = 0,
                total = newPaths.size
            )
            emit(progress)

            val (newUnmatchedPaths, persistedNewGames) = processNewGames(library, newPaths, progress)

            val (removedGames) = updateLibrary(
                library,
                removedUnmatchedPaths,
                newUnmatchedPaths,
                removedGamePaths
            )

            // 3. Finish scan
            progress.currentStep = LibraryScanStep(
                description = "Finishing up",
                current = 0,
                total = persistedNewGames.size
            )
            emit(progress)

            finishScanPersisted(persistedNewGames, library, progress)

            // 4. Send final progress update
            progress.currentStep = LibraryScanStep(description = "Finished")
            progress.finishedAt = Instant.now()
            progress.status = LibraryScanStatus.COMPLETED
            progress.result = FullScanResult(
                new = persistedNewGames.size,
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

    private fun processNewGames(
        library: Library,
        gamePaths: List<Path>,
        progress: LibraryScanProgress
    ): MatchNewGamesResult {
        val completed = AtomicInteger(0)
        val newUnmatchedPaths = ConcurrentHashMap.newKeySet<String>()

        val tasks = gamePaths.map { path ->
            Callable<Game?> {
                try {
                    val persisted = libraryGameProcessor.processNewGame(path, library)
                    return@Callable persisted
                } catch (e: Exception) {
                    // If not identified or any error, mark as unmatched
                    newUnmatchedPaths.add(path.toString())
                    log.warn { "Processing of new game at '$path' failed: ${e.message}" }
                    log.debug(e) {}
                    return@Callable null
                } finally {
                    progress.currentStep.current = completed.incrementAndGet()
                    emit(progress)
                }
            }
        }

        val persistedGames = executor.invokeAll(tasks).mapNotNull { it.get() }

        return MatchNewGamesResult(
            unmatchedPaths = newUnmatchedPaths.toList(),
            matchedGames = persistedGames // now these are already persisted
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

    private fun finishScanPersisted(games: List<Game>, library: Library, progress: LibraryScanProgress) {
        // Reload managed instances within a single persistence context to avoid merging multiple detached
        // representations of the same entity (e.g., Company) coming from parallel transactions.
        val libraryId = library.id ?: throw IllegalStateException("Library must have an ID")
        val managedLibrary = libraryRepository.findById(libraryId).orElseThrow()

        val gameIds = games.mapNotNull { it.id }
        val managedGames = if (gameIds.isNotEmpty()) gameRepository.findAllById(gameIds) else emptyList()

        // Add new games to library using managed entities, but do not persist yet
        libraryCoreService.addGamesToLibrary(managedGames, managedLibrary, persist = false)

        // Add new unmatched paths to library using managed entity, but do not persist yet
        managedLibrary.unmatchedPaths.addAll(
            library.unmatchedPaths.filter { it !in managedLibrary.unmatchedPaths }
        )

        progress.currentStep.current = games.size
        emit(progress)

        // Persist library updates
        managedLibrary.updatedAt = Instant.now() // Force the EntityListener to update the timestamp
        libraryRepository.save(managedLibrary)
    }

    private fun updateExistingGames(
        games: List<Game>,
        progress: LibraryScanProgress
    ): UpdateExistingGamesResult {
        val completedUpdates = AtomicInteger(0)

        val updateTasks = games.map { game ->
            Callable<Game?> {
                try {
                    val updated = libraryGameProcessor.processExistingGame(game)
                    return@Callable updated
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

        val updatedGames = executor.invokeAll(updateTasks).mapNotNull { it.get() }
        return UpdateExistingGamesResult(updatedGames = updatedGames)
    }
}