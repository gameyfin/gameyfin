package org.gameyfin.app.libraries

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.events.LibraryCreatedEvent
import org.gameyfin.app.core.events.LibraryDeletedEvent
import org.gameyfin.app.core.events.LibraryFilesystemWatcherConfigUpdatedEvent
import org.gameyfin.app.core.events.LibraryUpdatedEvent
import org.gameyfin.app.core.filesystem.FilesystemService
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.libraries.enums.ScanType
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.nio.file.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.isDirectory

/**
 * Service that monitors library directories for file system changes and automatically
 * updates games and libraries when files are added, removed, or modified.
 */
@Service
class LibraryWatcherService(
    private val libraryRepository: LibraryRepository,
    private val libraryScanService: LibraryScanService,
    private val gameRepository: GameRepository,
    private val filesystemService: FilesystemService,
    private val configService: ConfigService
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    data class LibraryWatchInfo(
        val libraryId: Long,
        val path: Path
    )

    private var watchService: WatchService? = null
    private val watchKeys = ConcurrentHashMap<WatchKey, LibraryWatchInfo>()
    private val libraryWatchers = ConcurrentHashMap<Long, MutableList<WatchKey>>()
    private var executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "library-watcher-thread").apply { isDaemon = true }
    }
    private val running = AtomicBoolean(false)

    @PostConstruct
    fun start() {
        // Check if filesystem watcher is enabled in config
        val isEnabled = configService.get(ConfigProperties.Libraries.Scan.EnableFilesystemWatcher) ?: false

        if (!isEnabled) {
            log.debug { "Library Watcher Service is disabled in configuration" }
            return
        }

        log.debug { "Starting Library Watcher Service" }

        // Create a new watch service if needed
        if (watchService == null) {
            watchService = FileSystems.getDefault().newWatchService()
        }

        // Recreate executor if it was previously shut down
        if (executor.isShutdown) {
            executor = Executors.newSingleThreadExecutor { r ->
                Thread(r, "library-watcher-thread").apply { isDaemon = true }
            }
        }

        running.set(true)

        // Start watching all existing libraries
        val libraries = libraryRepository.findAll()
        libraries.forEach { library ->
            startWatchingLibrary(library)
        }

        // Start the watch service thread
        executor.submit {
            watchForChanges()
        }

        log.info { "Library Watcher Service started, monitoring ${libraries.size} libraries" }
    }

    @PreDestroy
    fun stop() {
        log.debug { "Stopping Library Watcher Service" }
        running.set(false)

        // Close all watch keys
        watchKeys.keys.forEach { it.cancel() }
        watchKeys.clear()
        libraryWatchers.clear()

        // Shutdown executor
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }

        // Close watch service
        watchService?.close()
        watchService = null
        log.info { "Library Watcher Service stopped" }
    }

    @Async
    @EventListener(LibraryCreatedEvent::class)
    fun onLibraryCreated(event: LibraryCreatedEvent) {
        if (!running.get()) {
            log.debug { "Library created event received but watcher is not running, skipping" }
            return
        }
        log.debug { "Library created event received for library ${event.library.id}" }
        startWatchingLibrary(event.library)
    }

    @Async
    @EventListener(LibraryUpdatedEvent::class)
    fun onLibraryUpdated(event: LibraryUpdatedEvent) {
        if (!running.get()) {
            log.debug { "Library updated event received but watcher is not running, skipping" }
            return
        }
        log.debug { "Library updated event received for library ${event.currentState.id}" }
        // Stop watching the old directories
        stopWatchingLibrary(event.currentState.id!!)
        // Start watching the new directories
        startWatchingLibrary(event.currentState)
    }

    @Async
    @EventListener(LibraryDeletedEvent::class)
    fun onLibraryDeleted(event: LibraryDeletedEvent) {
        if (!running.get()) {
            log.debug { "Library deleted event received but watcher is not running, skipping" }
            return
        }
        log.debug { "Library deleted event received for library ${event.library.id}" }
        stopWatchingLibrary(event.library.id!!)
    }

    @Async
    @EventListener(LibraryFilesystemWatcherConfigUpdatedEvent::class)
    fun onFilesystemWatcherConfigUpdated(event: LibraryFilesystemWatcherConfigUpdatedEvent) {
        log.debug { "Filesystem watcher configuration updated" }

        if (event.isEnabled && !running.get()) {
            // Configuration changed to enabled and watcher is not running - start it
            log.debug { "Filesystem watcher enabled, starting watchers" }
            start()
        } else if (!event.isEnabled && running.get()) {
            // Configuration changed to disabled and watcher is running - stop it
            log.debug { "Filesystem watcher disabled, stopping watchers" }
            stop()
        }
    }

    private fun startWatchingLibrary(library: Library) {
        val libraryId = library.id ?: return

        log.debug { "Starting to watch library '${library.name}' (ID: $libraryId)" }

        library.directories.forEach { directoryMapping ->
            try {
                val path = Paths.get(directoryMapping.internalPath)

                if (!path.isDirectory()) {
                    log.warn { "Path is not a directory: $path" }
                    return@forEach
                }

                // Register the directory with the watch service
                val service = watchService
                if (service == null) {
                    log.warn { "Watch service is not initialized, cannot watch directory: $path" }
                    return@forEach
                }

                val watchKey = path.register(
                    service,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY
                )

                val watchInfo = LibraryWatchInfo(libraryId, path)
                watchKeys[watchKey] = watchInfo
                libraryWatchers.computeIfAbsent(libraryId) { mutableListOf() }.add(watchKey)

                log.debug { "Registered watcher for directory: $path in library $libraryId" }
            } catch (e: Exception) {
                log.error(e) { "Failed to register watcher for directory: ${directoryMapping.internalPath}" }
            }
        }
    }

    private fun stopWatchingLibrary(libraryId: Long) {
        log.debug { "Stopping watchers for library $libraryId" }

        libraryWatchers[libraryId]?.forEach { watchKey ->
            watchKey.cancel()
            watchKeys.remove(watchKey)
        }
        libraryWatchers.remove(libraryId)

        log.debug { "Stopped all watchers for library $libraryId" }
    }

    private fun watchForChanges() {
        log.debug { "Watch service thread started" }

        while (running.get()) {
            try {
                val watchKey = watchService?.poll(1, TimeUnit.SECONDS) ?: continue
                val watchInfo = watchKeys[watchKey] ?: continue

                val events = watchKey.pollEvents()
                if (events.isEmpty()) {
                    watchKey.reset()
                    continue
                }

                log.debug { "Detected ${events.size} file system events in library ${watchInfo.libraryId}" }

                // Group events by type
                val hasCreates = events.any { it.kind() == StandardWatchEventKinds.ENTRY_CREATE }
                val hasDeletes = events.any { it.kind() == StandardWatchEventKinds.ENTRY_DELETE }
                val hasModifies = events.any { it.kind() == StandardWatchEventKinds.ENTRY_MODIFY }

                // Process the events
                processFileSystemEvents(watchInfo, events, hasCreates, hasDeletes, hasModifies)

                // Reset the watch key
                if (!watchKey.reset()) {
                    log.warn { "Watch key no longer valid for path: ${watchInfo.path}" }
                    watchKeys.remove(watchKey)
                    libraryWatchers[watchInfo.libraryId]?.remove(watchKey)
                }
            } catch (_: InterruptedException) {
                log.debug { "Watch service thread interrupted" }
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                log.error(e) { "Error processing file system events" }
            }
        }

        log.debug { "Watch service thread stopped" }
    }

    private fun processFileSystemEvents(
        watchInfo: LibraryWatchInfo,
        events: List<WatchEvent<*>>,
        hasCreates: Boolean,
        hasDeletes: Boolean,
        hasModifies: Boolean
    ) {
        try {
            val library = libraryRepository.findById(watchInfo.libraryId).orElse(null)
            if (library == null) {
                log.warn { "Library ${watchInfo.libraryId} not found, stopping watcher" }
                stopWatchingLibrary(watchInfo.libraryId)
                return
            }

            if (events.isEmpty()) {
                log.debug { "No relevant game file changes detected" }
                return
            }

            log.debug {
                "Processing ${events.size} relevant file changes in library '${library.name}' " +
                        "(creates: $hasCreates, deletes: $hasDeletes, modifies: $hasModifies)"
            }

            // Handle creates (new games)
            if (hasCreates) {
                handleCreates(library, events.filter {
                    it.kind() == StandardWatchEventKinds.ENTRY_CREATE
                })
            }

            // Handle deletes (removed games)
            if (hasDeletes) {
                handleDeletes(library, events.filter {
                    it.kind() == StandardWatchEventKinds.ENTRY_DELETE
                })
            }

            // Handle modifies (changed file sizes)
            if (hasModifies) {
                handleModifies(library, watchInfo, events.filter {
                    it.kind() == StandardWatchEventKinds.ENTRY_MODIFY
                })
            }

        } catch (e: Exception) {
            log.error(e) { "Error processing file system events for library ${watchInfo.libraryId}" }
        }
    }

    private fun handleCreates(library: Library, events: List<WatchEvent<*>>) {
        log.debug { "Handling ${events.size} create events for library ${library.id}" }

        // Trigger a quick scan to add new games
        // The scan service will handle the actual game creation
        libraryScanService.triggerScan(ScanType.QUICK, listOf(library.id!!))
    }

    private fun handleDeletes(library: Library, events: List<WatchEvent<*>>) {
        log.debug { "Handling ${events.size} delete events for library ${library.id}" }

        // Trigger a quick scan to remove deleted games
        // The scan service will handle the actual game deletion
        libraryScanService.triggerScan(ScanType.QUICK, listOf(library.id!!))
    }

    private fun handleModifies(library: Library, watchInfo: LibraryWatchInfo, events: List<WatchEvent<*>>) {
        log.debug { "Handling ${events.size} modify events for library ${library.id}" }

        events.forEach { event ->
            @Suppress("UNCHECKED_CAST")
            val watchEvent = event as WatchEvent<Path>
            val filename = watchEvent.context()
            val fullPath = watchInfo.path.resolve(filename)

            // Find games that match this path and update their file size
            val gamesToUpdate = library.games.filter { game ->
                game.metadata.path == fullPath.toString()
            }

            if (gamesToUpdate.isNotEmpty()) {
                log.debug { "Updating file size for ${gamesToUpdate.size} games: $fullPath" }
                gamesToUpdate.forEach { game ->
                    val newFileSize = filesystemService.calculateFileSize(game.metadata.path)
                    if (game.metadata.fileSize != newFileSize) {
                        game.metadata.fileSize = newFileSize
                        gameRepository.save(game)
                        log.debug { "Updated file size for game '${game.title}' from ${game.metadata.fileSize} to $newFileSize bytes" }
                    }
                }
            }
        }
    }
}

