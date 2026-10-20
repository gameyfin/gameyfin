package org.gameyfin.app.libraries

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.filesystem.FilesystemService
import org.gameyfin.app.core.metrics.ScanMetrics
import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.core.tasks.LIBRARY_SCAN_TASK_KEY
import org.gameyfin.app.core.tasks.TaskAction
import org.gameyfin.app.core.tasks.TaskDefinition
import org.gameyfin.app.core.tasks.TaskRegistry
import org.gameyfin.app.core.tasks.TaskRunService
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.entities.IgnoredPath
import org.gameyfin.app.libraries.entities.IgnoredPathPluginSource
import org.gameyfin.app.libraries.entities.IgnoredPathSourceType
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.libraries.enums.ScanType
import org.gameyfin.app.libraries.scan.LibraryGameProcessor
import org.gameyfin.app.libraries.scan.MatchNewGamesResult
import org.gameyfin.app.libraries.scan.UpdateExistingGamesResult
import org.gameyfin.app.libraries.scan.UpdateLibraryResult
import org.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import org.gameyfin.pluginapi.tasks.TaskCancelledException
import org.gameyfin.pluginapi.tasks.TaskContext
import org.gameyfin.pluginapi.tasks.TaskResult
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.pf4j.PluginState
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

@Service
class LibraryScanService(
    private val libraryRepository: LibraryRepository,
    private val filesystemService: FilesystemService,
    private val libraryCoreService: LibraryCoreService,
    private val libraryGameProcessor: LibraryGameProcessor,
    private val gameRepository: GameRepository,
    private val ignoredPathRepository: IgnoredPathRepository,
    private val pluginService: PluginService,
    private val configService: ConfigService,
    private val scanMetrics: ScanMetrics,
    private val taskRegistry: TaskRegistry,
    private val taskRunService: TaskRunService
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private var scanConcurrency = ConfigProperties.Libraries.Scan.MaxConcurrency.default!!
        @Volatile
        private var scanSemaphore = Semaphore(scanConcurrency)
        private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

        /** Precedence of triggers when multiple scan requests for a library are coalesced */
        private val TRIGGER_PRECEDENCE = listOf(
            TaskTrigger.WATCHER,
            TaskTrigger.SYSTEM,
            TaskTrigger.SCHEDULE,
            TaskTrigger.MANUAL
        )
    }

    private val scanQueue = LibraryScanQueue(scan = ::runQueuedScan)

    /** What caused the pending scan of a library */
    private val requestedTriggers = ConcurrentHashMap<Long, TaskTrigger>()

    @PostConstruct
    fun registerTask() {
        taskRegistry.register(
            TaskDefinition(
                key = LIBRARY_SCAN_TASK_KEY,
                name = "Library scan",
                description = "Scans all libraries for new, removed and updated games and refreshes their metadata. " +
                        "Every library is scanned in its own run.",
                defaultSchedule = "@daily",
                action = TaskAction.Dispatch { trigger -> triggerScan(ScanType.SCHEDULED, null, trigger) }
            )
        )
    }

    @PreDestroy
    fun stop() {
        scanQueue.close()
    }

    /** Watcher bursts collapse into a single scan after 30 seconds of quiet. */
    fun scheduleScan(scanType: ScanType, library: Library) {
        library.id?.let {
            requestTrigger(it, TaskTrigger.WATCHER)
            scanQueue.request(it, scanType, debounce = true)
        }
    }

    /**
     * Updates the concurrency semaphore when the configured limit changes.
     * Reuse the current semaphore across libraries so starting another scan
     * does not bypass permits already held by running game tasks.
     */
    private fun refreshScanSemaphore() {
        val permits = configService.get(ConfigProperties.Libraries.Scan.MaxConcurrency)!!
        synchronized(Companion) {
            if (permits != scanConcurrency) {
                scanSemaphore = Semaphore(permits)
                scanConcurrency = permits
            }
        }
    }

    /**
     * Wrapper function to trigger a scan for a list of libraries.
     */
    fun triggerScan(scanType: ScanType, libraryIds: Collection<Long>?, trigger: TaskTrigger = TaskTrigger.MANUAL) {
        check(pluginService.getAllByTypeAndState(GameMetadataProvider::class, PluginState.STARTED).isNotEmpty()) {
            "At least one metadata plugin must be enabled to perform a scan."
        }

        val libraries =
            if (libraryIds.isNullOrEmpty()) libraryRepository.findAll().toList()
            else libraryRepository.findAllById(libraryIds).toList()

        libraries.forEach { library ->
            requestTrigger(library.id!!, trigger)
            scanQueue.request(library.id!!, scanType, debounce = false)
        }
    }

    private fun requestTrigger(libraryId: Long, trigger: TaskTrigger) {
        requestedTriggers.merge(libraryId, trigger) { current, requested ->
            if (TRIGGER_PRECEDENCE.indexOf(requested) > TRIGGER_PRECEDENCE.indexOf(current)) requested else current
        }
    }

    private fun runQueuedScan(libraryId: Long, scanType: ScanType) {
        val trigger = requestedTriggers.remove(libraryId) ?: TaskTrigger.MANUAL
        // Follow-up scans must use current library contents, not the previous scan's entity graph.
        val library = libraryCoreService.getWithGamesAndIgnoredPaths(libraryId) ?: return
        val scanName = when (scanType) {
            ScanType.QUICK -> "Quick scan"
            ScanType.FULL -> "Full scan"
            ScanType.SCHEDULED -> "Scheduled scan"
        }

        taskRunService.runBlocking(
            taskKey = LIBRARY_SCAN_TASK_KEY,
            trigger = trigger,
            displayName = "$scanName of library '${library.name}'",
            link = "/administration/games/library/$libraryId",
            rethrow = false
        ) { context ->
            check(pluginService.getAllByTypeAndState(GameMetadataProvider::class, PluginState.STARTED).isNotEmpty()) {
                "At least one metadata plugin must be enabled to perform a scan."
            }
            refreshScanSemaphore()
            when (scanType) {
                ScanType.QUICK -> quickScan(library, context)
                ScanType.FULL -> fullScan(library, false, context)
                ScanType.SCHEDULED -> fullScan(library, true, context)
            }
        }
    }

    private fun quickScan(library: Library, context: TaskContext): TaskResult {
        context.progress("Scanning filesystem")
        scanMetrics.recordScanStarted(ScanType.QUICK)
        val scanStartTime = System.currentTimeMillis()

        try {
            val scanData = performFilesystemScan(library)
            context.throwIfCancelled()

            // 1. Process each new game independently (including re-scanned plugin ignored paths)
            val (newUnmatchedPaths, persistedNewGames) = processNewGames(library, scanData.allPathsToProcess, context)

            // 2. Update library (removed games/ignored paths, and add persisted new ones)
            val (removedGames) = updateLibrary(
                library,
                scanData.removedIgnoredPaths,
                newUnmatchedPaths,
                scanData.removedGamePaths
            )

            // 3. Finish scan: persist library changes and report
            finishScan(persistedNewGames, library, context)

            scanMetrics.recordScanCompleted(
                type = ScanType.QUICK,
                durationMillis = System.currentTimeMillis() - scanStartTime,
                newGames = persistedNewGames.size,
                removedGames = removedGames.size,
                unmatchedPaths = newUnmatchedPaths.size
            )

            val summary = "${persistedNewGames.size} new, ${removedGames.size} removed, " +
                    "${newUnmatchedPaths.size} unmatched"
            return completeScan(library, summary, context)
        } catch (e: TaskCancelledException) {
            throw e
        } catch (e: Exception) {
            scanMetrics.recordScanFailed(ScanType.QUICK, System.currentTimeMillis() - scanStartTime)
            throw e
        }
    }

    private fun fullScan(library: Library, triggeredBySchedule: Boolean, context: TaskContext): TaskResult {
        val scanType = if (triggeredBySchedule) ScanType.SCHEDULED else ScanType.FULL
        context.progress("Scanning filesystem")
        scanMetrics.recordScanStarted(scanType)
        val scanStartTime = System.currentTimeMillis()

        try {
            val scanData = performFilesystemScan(library)
            context.throwIfCancelled()

            // 1. Update existing games (individually)
            val (updatedGames) = updateExistingGames(library.games, context)

            // 2. Process new games (individually, including re-scanned plugin ignored paths)
            val (newUnmatchedPaths, persistedNewGames) = processNewGames(library, scanData.allPathsToProcess, context)

            val (removedGames) = updateLibrary(
                library,
                scanData.removedIgnoredPaths,
                newUnmatchedPaths,
                scanData.removedGamePaths
            )

            // 3. Finish scan
            finishScan(persistedNewGames, library, context)

            scanMetrics.recordScanCompleted(
                type = scanType,
                durationMillis = System.currentTimeMillis() - scanStartTime,
                newGames = persistedNewGames.size,
                removedGames = removedGames.size,
                unmatchedPaths = newUnmatchedPaths.size,
                updatedGames = updatedGames.size
            )

            val summary = "${persistedNewGames.size} new, ${updatedGames.size} updated, " +
                    "${removedGames.size} removed, ${newUnmatchedPaths.size} unmatched"
            return completeScan(library, summary, context)
        } catch (e: TaskCancelledException) {
            throw e
        } catch (e: Exception) {
            scanMetrics.recordScanFailed(scanType, System.currentTimeMillis() - scanStartTime)
            throw e
        }
    }

    /**
     * Games which have already been processed when a scan is cancelled are kept, so the result of a cancelled scan
     * is reported as summary of the cancelled run.
     */
    private fun completeScan(library: Library, summary: String, context: TaskContext): TaskResult {
        if (context.isCancellationRequested) {
            log.info { "Scan of library '${library.name}' has been cancelled: $summary" }
            throw TaskCancelledException(summary)
        }
        log.info { "Scan of library '${library.name}' finished: $summary" }
        return TaskResult(summary)
    }

    private data class FilesystemScanData(
        val allPathsToProcess: List<Path>,
        val removedGamePaths: List<String>,
        val removedIgnoredPaths: List<IgnoredPath>
    )

    private fun performFilesystemScan(library: Library): FilesystemScanData {
        val scanResult = filesystemService.scanLibraryForGamefiles(library)
        val newPaths = scanResult.newPaths
        val removedGamePaths = scanResult.removedGamePaths.map { it.toString() }
        val removedIgnoredPaths = scanResult.removedIgnoredPaths

        // Get plugin-generated (system) ignored paths to re-scan
        // Skip paths that no longer exist on the filesystem, otherwise they get re-added as unmatched
        val removedIgnoredPathIds = removedIgnoredPaths.map { it.path }.toSet()
        val pluginIgnoredPathsToRescan = library.ignoredPaths
            .filter { it.getType() == IgnoredPathSourceType.PLUGIN && it.path !in removedIgnoredPathIds }
            .map { Path.of(it.path) }

        val allPathsToProcess = newPaths + pluginIgnoredPathsToRescan

        return FilesystemScanData(
            allPathsToProcess = allPathsToProcess,
            removedGamePaths = removedGamePaths,
            removedIgnoredPaths = removedIgnoredPaths
        )
    }

    private fun processNewGames(
        library: Library,
        gamePaths: List<Path>,
        context: TaskContext
    ): MatchNewGamesResult {
        val step = "Processing new games"
        context.progress(step, 0, gamePaths.size)
        val completed = AtomicInteger(0)
        val newUnmatchedPaths = ConcurrentHashMap.newKeySet<IgnoredPath>()
        val semaphore = scanSemaphore

        val tasks = gamePaths.map { path ->
            context.wrap(Callable<Game?> {
                // Skipped paths are neither added to the library nor marked as unmatched, so they are picked up again
                if (context.isCancellationRequested) return@Callable null
                semaphore.acquire()
                try {
                    val persisted = logProcessingTime("new game '${path.fileName}'") {
                        libraryGameProcessor.processNewGame(path, library)
                    }

                    if (persisted == null) {
                        // Not identified, mark as unmatched by all current metadata providers
                        val pluginSource = IgnoredPathPluginSource(
                            pluginService.getPluginManagementEntries(GameMetadataProvider::class.java).toMutableList()
                        )
                        val ignoredPath = IgnoredPath(
                            path = path.toString(),
                            source = pluginSource
                        )
                        newUnmatchedPaths.add(ignoredPath)
                    }

                    return@Callable persisted
                } catch (e: Exception) {
                    // Error, mark as unmatched by all current metadata providers
                    val pluginSource = IgnoredPathPluginSource(
                        pluginService.getPluginManagementEntries(GameMetadataProvider::class.java).toMutableList()
                    )
                    val ignoredPath = IgnoredPath(
                        path = path.toString(),
                        source = pluginSource
                    )
                    newUnmatchedPaths.add(ignoredPath)

                    log.warn { "Processing of new game at '$path' failed: ${e.message}" }
                    log.debug(e) {}

                    return@Callable null
                } finally {
                    semaphore.release()
                    context.progress(step, completed.incrementAndGet(), gamePaths.size)
                }
            })
        }

        val persistedGames = collectTaskResults(tasks)

        return MatchNewGamesResult(
            unmatchedPaths = newUnmatchedPaths.toList(),
            matchedGames = persistedGames // now these are already persisted
        )
    }

    private fun updateLibrary(
        library: Library,
        removedIgnoredPaths: List<IgnoredPath>,
        newIgnoredPaths: List<IgnoredPath>,
        removedGamePaths: List<String>
    ): UpdateLibraryResult {
        // 1.2 Remove old ignored paths from the library
        val removedIgnoredPathIds = removedIgnoredPaths.mapNotNull { it.id }.toSet()
        library.ignoredPaths.removeIf { it.id != null && it.id in removedIgnoredPathIds }

        // Add new unmatched paths to the library, but check if they already exist
        val pathsToAdd = mutableListOf<IgnoredPath>()
        for (newPath in newIgnoredPaths) {
            val existingPath = ignoredPathRepository.findByPath(newPath.path)
            if (existingPath != null) {
                // Use the existing path if not already in library's collection
                if (!library.ignoredPaths.any { it.id == existingPath.id }) {
                    pathsToAdd.add(existingPath)
                }
            } else {
                // Add new path
                pathsToAdd.add(newPath)
            }
        }
        library.ignoredPaths.addAll(pathsToAdd)

        // 1.3 Remove deleted games from the library
        val removedGames = library.games.filter { removedGamePaths.contains(it.metadata.path) }
        library.games.removeAll(removedGames)

        return UpdateLibraryResult(removedGames = removedGames)
    }

    private fun finishScan(games: List<Game>, library: Library, context: TaskContext) {
        context.progress("Finishing up", 0, games.size)
        val gameIds = games.mapNotNull { it.id }
        val managedGames = if (gameIds.isNotEmpty()) gameRepository.findAllById(gameIds) else emptyList()

        // Add new games to library using managed entities, but do not persist yet
        libraryCoreService.addGamesToLibrary(managedGames, library, persist = false)

        context.progress("Finishing up", games.size, games.size)

        // Persist library updates
        library.updatedAt = Instant.now() // Force the EntityListener to update the timestamp
        libraryRepository.save(library)
    }

    private fun updateExistingGames(
        games: List<Game>,
        context: TaskContext
    ): UpdateExistingGamesResult {
        val step = "Updating existing games"
        context.progress(step, 0, games.size)
        val completedUpdates = AtomicInteger(0)
        val semaphore = scanSemaphore

        val updateTasks = games.map { game ->
            context.wrap(Callable<Game?> {
                if (context.isCancellationRequested) return@Callable null
                semaphore.acquire()
                try {
                    val updated = logProcessingTime("existing game '${game.title}' (id=${game.id})") {
                        libraryGameProcessor.processExistingGame(game)
                    }
                    return@Callable updated
                } catch (e: Exception) {
                    log.error { "Error updating game with id '${game.id}': ${e.message}" }
                    log.debug(e) {}
                    return@Callable null
                } finally {
                    semaphore.release()
                    context.progress(step, completedUpdates.incrementAndGet(), games.size)
                }
            })
        }

        val updatedGames = collectTaskResults(updateTasks)
        return UpdateExistingGamesResult(updatedGames = updatedGames)
    }

    private fun <T : Any> collectTaskResults(tasks: List<Callable<T?>>): List<T> {
        return executor.invokeAll(tasks).mapNotNull { future ->
            try {
                future.get()
            } catch (e: ExecutionException) {
                log.warn(e.cause ?: e) { "Game processing task failed after completion" }
                null
            } catch (_: CancellationException) {
                log.warn { "Game processing task was cancelled" }
                null
            }
        }
    }

    private fun <T> logProcessingTime(description: String, process: () -> T): T {
        val startedAt = System.nanoTime()
        log.debug { "Processing $description" }
        try {
            return process()
        } finally {
            val elapsed = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAt)
            if (elapsed > 60) {
                log.warn { "Processing $description took ${elapsed}s" }
            } else {
                log.debug { "Finished processing $description in ${elapsed}s" }
            }
        }
    }
}
