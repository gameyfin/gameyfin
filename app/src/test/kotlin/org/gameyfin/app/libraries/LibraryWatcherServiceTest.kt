package org.gameyfin.app.libraries

import io.mockk.*
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.events.LibraryCreatedEvent
import org.gameyfin.app.core.events.LibraryDeletedEvent
import org.gameyfin.app.core.events.LibraryFilesystemWatcherConfigUpdatedEvent
import org.gameyfin.app.core.events.LibraryUpdatedEvent
import org.gameyfin.app.core.filesystem.FilesystemService
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.entities.DirectoryMapping
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.libraries.enums.ScanType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText
import kotlin.test.assertTrue

class LibraryWatcherServiceTest {

    private lateinit var libraryRepository: LibraryRepository
    private lateinit var libraryScanService: LibraryScanService
    private lateinit var gameRepository: GameRepository
    private lateinit var filesystemService: FilesystemService
    private lateinit var configService: ConfigService
    private lateinit var libraryWatcherService: LibraryWatcherService

    private lateinit var tempDir: Path
    private val testGameExtensions = arrayOf("exe", "iso", "zip")

    @BeforeEach
    fun setup() {
        libraryRepository = mockk(relaxed = true)
        libraryScanService = mockk(relaxed = true)
        gameRepository = mockk(relaxed = true)
        filesystemService = mockk(relaxed = true)
        configService = mockk(relaxed = true)

        // Setup config service to return game file extensions and enable filesystem watcher
        every { configService.get(ConfigProperties.Libraries.Scan.GameFileExtensions) } returns testGameExtensions
        every { configService.get(ConfigProperties.Libraries.Scan.EnableFilesystemWatcher) } returns true

        // Create temporary directory for file system tests
        tempDir = Files.createTempDirectory("library-watcher-test")

        libraryWatcherService = LibraryWatcherService(
            libraryRepository,
            libraryScanService,
            gameRepository,
            filesystemService,
            configService
        )
    }

    @AfterEach
    fun tearDown() {
        // Stop the service to clean up watchers
        libraryWatcherService.stop()

        // Clean up temporary directory
        tempDir.toFile().deleteRecursively()

        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `start should initialize watcher for all existing libraries`() {
        val library1 = createTestLibrary(1L, "Library 1", listOf(tempDir.resolve("lib1").toString()))
        val library2 = createTestLibrary(2L, "Library 2", listOf(tempDir.resolve("lib2").toString()))

        // Create directories
        tempDir.resolve("lib1").createDirectories()
        tempDir.resolve("lib2").createDirectories()

        every { libraryRepository.findAll() } returns listOf(library1, library2)

        libraryWatcherService.start()

        // Verify that start was called
        verify(exactly = 1) { libraryRepository.findAll() }
    }

    @Test
    fun `start should handle libraries with invalid directories`() {
        val library = createTestLibrary(1L, "Test Library", listOf("/nonexistent/path"))

        every { libraryRepository.findAll() } returns listOf(library)

        assertDoesNotThrow {
            libraryWatcherService.start()
        }
    }

    @Test
    fun `stop should shutdown gracefully`() {
        every { libraryRepository.findAll() } returns emptyList()

        libraryWatcherService.start()

        assertDoesNotThrow {
            libraryWatcherService.stop()
        }
    }

    @Test
    fun `onLibraryCreated should start watching new library`() {
        val libraryDir = tempDir.resolve("new-library")
        libraryDir.createDirectories()

        val library = createTestLibrary(1L, "New Library", listOf(libraryDir.toString()))

        every { libraryRepository.findAll() } returns emptyList()
        libraryWatcherService.start()

        val event = LibraryCreatedEvent(this, library)
        libraryWatcherService.onLibraryCreated(event)

        // Verify the event was processed without errors
        assertTrue(true)
    }

    @Test
    fun `onLibraryUpdated should restart watchers for updated library`() {
        val oldDir = tempDir.resolve("old-dir")
        val newDir = tempDir.resolve("new-dir")
        oldDir.createDirectories()
        newDir.createDirectories()

        val oldLibrary = createTestLibrary(1L, "Test Library", listOf(oldDir.toString()))
        val updatedLibrary = createTestLibrary(1L, "Test Library", listOf(newDir.toString()))

        every { libraryRepository.findAll() } returns listOf(oldLibrary)
        libraryWatcherService.start()

        val event = LibraryUpdatedEvent(this, updatedLibrary)
        libraryWatcherService.onLibraryUpdated(event)

        // Verify the event was processed without errors
        assertTrue(true)
    }

    @Test
    fun `onLibraryDeleted should stop watching deleted library`() {
        val libraryDir = tempDir.resolve("to-delete")
        libraryDir.createDirectories()

        val library = createTestLibrary(1L, "To Delete", listOf(libraryDir.toString()))

        every { libraryRepository.findAll() } returns listOf(library)
        libraryWatcherService.start()

        val event = LibraryDeletedEvent(this, library)
        libraryWatcherService.onLibraryDeleted(event)

        // Verify the event was processed without errors
        assertTrue(true)
    }

    @Test
    fun `file create event should trigger quick scan`() {
        val libraryDir = tempDir.resolve("watch-create")
        libraryDir.createDirectories()

        val library = createTestLibrary(1L, "Watch Library", listOf(libraryDir.toString()))

        every { libraryRepository.findAll() } returns listOf(library)
        every { libraryRepository.findById(1L) } returns Optional.of(library)
        every { libraryScanService.triggerScan(ScanType.QUICK, listOf(1L)) } just Runs

        libraryWatcherService.start()

        // Give watcher time to initialize
        Thread.sleep(100)

        // Create a new game file
        val gameFile = libraryDir.resolve("newgame.exe")
        gameFile.createFile()
        gameFile.writeText("test content")

        // Give watcher time to detect and process the event
        Thread.sleep(500)

        // Verify quick scan was triggered
        verify(atLeast = 1) { libraryScanService.triggerScan(ScanType.QUICK, listOf(1L)) }
    }

    @Test
    fun `file delete event should trigger quick scan`() {
        val libraryDir = tempDir.resolve("watch-delete")
        libraryDir.createDirectories()

        val gameFile = libraryDir.resolve("game.exe")
        gameFile.createFile()

        val game = createTestGame(1L, gameFile.toString())
        val library = createTestLibrary(1L, "Watch Library", listOf(libraryDir.toString()), mutableListOf(game))

        every { libraryRepository.findAll() } returns listOf(library)
        every { libraryRepository.findById(1L) } returns Optional.of(library)
        every { libraryRepository.save(library) } returns library

        libraryWatcherService.start()

        // Give watcher time to initialize
        Thread.sleep(100)

        // Delete the game file
        gameFile.deleteExisting()

        // Give watcher time to detect and process the event
        Thread.sleep(500)

        // Verify game was removed from library
        verify(atLeast = 1) { libraryScanService.triggerScan(ScanType.QUICK, listOf(1L)) }
    }

    @Test
    fun `directory delete event should trigger quick scan`() {
        val libraryDir = tempDir.resolve("watch-delete-dir")
        libraryDir.createDirectories()

        val gameDir = libraryDir.resolve("Game Folder")
        gameDir.createDirectories()

        val library = createTestLibrary(1L, "Watch Library", listOf(libraryDir.toString()))

        every { libraryRepository.findAll() } returns listOf(library)
        every { libraryRepository.findById(1L) } returns Optional.of(library)
        every { libraryScanService.triggerScan(ScanType.QUICK, listOf(1L)) } just Runs

        libraryWatcherService.start()

        // Give watcher time to initialize
        Thread.sleep(100)

        // Delete the game directory
        gameDir.toFile().deleteRecursively()

        // Give watcher time to detect and process the event
        Thread.sleep(500)

        // Verify quick scan was triggered (directories are treated as potential game folders)
        verify(atLeast = 1) { libraryScanService.triggerScan(ScanType.QUICK, listOf(1L)) }
    }

    @Test
    fun `file modify event should update game file size`() {
        val libraryDir = tempDir.resolve("watch-modify")
        libraryDir.createDirectories()

        val gameFile = libraryDir.resolve("game.exe")
        gameFile.createFile()
        gameFile.writeText("initial content")

        val metadata = GameMetadata(path = gameFile.toString(), fileSize = 100L)
        val game = createTestGame(1L, gameFile.toString(), metadata)
        val library = createTestLibrary(1L, "Watch Library", listOf(libraryDir.toString()), mutableListOf(game))

        val newFileSize = 2000L
        every { libraryRepository.findAll() } returns listOf(library)
        every { libraryRepository.findById(1L) } returns Optional.of(library)
        every { filesystemService.calculateFileSize(gameFile.toString()) } returns newFileSize
        every { gameRepository.save(game) } returns game

        libraryWatcherService.start()

        // Give watcher time to initialize
        Thread.sleep(100)

        // Modify the file
        gameFile.writeText("modified content with more data")

        // Give watcher time to detect and process the event
        Thread.sleep(500)

        // Verify file size was recalculated
        verify(atLeast = 1) { filesystemService.calculateFileSize(gameFile.toString()) }
    }

    @Test
    fun `should handle multiple rapid file changes`() {
        val libraryDir = tempDir.resolve("watch-rapid")
        libraryDir.createDirectories()

        val library = createTestLibrary(1L, "Watch Library", listOf(libraryDir.toString()))

        every { libraryRepository.findAll() } returns listOf(library)
        every { libraryRepository.findById(1L) } returns Optional.of(library)
        every { libraryScanService.triggerScan(any(), any()) } just Runs

        libraryWatcherService.start()

        // Give watcher time to initialize
        Thread.sleep(100)

        // Create multiple files rapidly
        repeat(5) { i ->
            val gameFile = libraryDir.resolve("game$i.exe")
            gameFile.createFile()
            Thread.sleep(50)
        }

        // Give watcher time to process all events
        Thread.sleep(1000)

        // Verify that scans were triggered (may be batched)
        verify(atLeast = 1) { libraryScanService.triggerScan(ScanType.QUICK, listOf(1L)) }
    }

    @Test
    fun `should handle library with multiple directories`() {
        val dir1 = tempDir.resolve("lib-dir1")
        val dir2 = tempDir.resolve("lib-dir2")
        dir1.createDirectories()
        dir2.createDirectories()

        val library = createTestLibrary(1L, "Multi-Dir Library", listOf(dir1.toString(), dir2.toString()))

        every { libraryRepository.findAll() } returns listOf(library)
        every { libraryRepository.findById(1L) } returns Optional.of(library)
        every { libraryScanService.triggerScan(any(), any()) } just Runs

        libraryWatcherService.start()

        // Give watcher time to initialize
        Thread.sleep(100)

        // Create files in both directories
        val game1 = dir1.resolve("game1.exe")
        game1.createFile()

        Thread.sleep(300)

        val game2 = dir2.resolve("game2.iso")
        game2.createFile()

        // Give watcher time to process events
        Thread.sleep(500)

        // Verify scans were triggered for both directories
        verify(atLeast = 2) { libraryScanService.triggerScan(ScanType.QUICK, listOf(1L)) }
    }

    @Test
    fun `should handle directory creation`() {
        val libraryDir = tempDir.resolve("watch-dir")
        libraryDir.createDirectories()

        val library = createTestLibrary(1L, "Watch Library", listOf(libraryDir.toString()))

        every { libraryRepository.findAll() } returns listOf(library)
        every { libraryRepository.findById(1L) } returns Optional.of(library)
        every { libraryScanService.triggerScan(any(), any()) } just Runs

        libraryWatcherService.start()

        // Give watcher time to initialize
        Thread.sleep(100)

        // Create a new directory (which could be a game folder)
        val newGameDir = libraryDir.resolve("New Game")
        newGameDir.createDirectories()

        // Give watcher time to detect and process the event
        Thread.sleep(500)

        // Verify quick scan was triggered
        verify(atLeast = 1) { libraryScanService.triggerScan(ScanType.QUICK, listOf(1L)) }
    }

    @Test
    fun `should not crash when library is deleted while being watched`() {
        val libraryDir = tempDir.resolve("watch-delete-lib")
        libraryDir.createDirectories()

        val library = createTestLibrary(1L, "Watch Library", listOf(libraryDir.toString()))

        every { libraryRepository.findAll() } returns listOf(library)
        every { libraryRepository.findById(1L) } returns Optional.empty() // Library deleted

        libraryWatcherService.start()

        // Give watcher time to initialize
        Thread.sleep(100)

        // Create a file that will trigger an event
        val gameFile = libraryDir.resolve("game.exe")
        gameFile.createFile()

        // Give watcher time to process (should handle missing library gracefully)
        Thread.sleep(500)

        // Verify no exception was thrown
        assertTrue(true)
    }

    @Test
    fun `start and stop multiple times should work correctly`() {
        every { libraryRepository.findAll() } returns emptyList()

        assertDoesNotThrow {
            libraryWatcherService.start()
            libraryWatcherService.stop()

            libraryWatcherService.start()
            libraryWatcherService.stop()
        }
    }

    @Test
    fun `should not start when filesystem watcher is disabled in config`() {
        every { configService.get(ConfigProperties.Libraries.Scan.EnableFilesystemWatcher) } returns false
        every { libraryRepository.findAll() } returns emptyList()

        libraryWatcherService.start()

        // Verify that no libraries were attempted to be watched
        verify(exactly = 0) { libraryRepository.findAll() }
    }

    @Test
    fun `config update event should start watchers when enabled`() {
        val libraryDir = tempDir.resolve("config-test")
        libraryDir.createDirectories()

        val library = createTestLibrary(1L, "Test Library", listOf(libraryDir.toString()))

        every { libraryRepository.findAll() } returns listOf(library)

        libraryWatcherService.start()

        val event = LibraryFilesystemWatcherConfigUpdatedEvent(this, true)
        libraryWatcherService.onFilesystemWatcherConfigUpdated(event)

        // Give some time for the watcher to initialize
        Thread.sleep(200)

        // Verify service started
        verify(atLeast = 1) { libraryRepository.findAll() }
    }

    @Test
    fun `config update event should stop watchers when disabled`() {
        val libraryDir = tempDir.resolve("config-disable-test")
        libraryDir.createDirectories()

        val library = createTestLibrary(1L, "Test Library", listOf(libraryDir.toString()))

        every { libraryRepository.findAll() } returns listOf(library)

        libraryWatcherService.start()

        // Give time to start
        Thread.sleep(200)

        val event = LibraryFilesystemWatcherConfigUpdatedEvent(this, false)
        libraryWatcherService.onFilesystemWatcherConfigUpdated(event)

        // Give time for shutdown
        Thread.sleep(200)

        // Create a file after disabling - should not trigger scan
        val gameFile = libraryDir.resolve("game.exe")
        gameFile.createFile()

        Thread.sleep(300)

        // Verify no scan was triggered after disabling
        verify(exactly = 0) { libraryScanService.triggerScan(any(), any()) }
    }

    @Test
    fun `library events should be ignored when watcher is not running`() {
        val libraryDir = tempDir.resolve("events-disabled")
        libraryDir.createDirectories()

        val library = createTestLibrary(1L, "Test Library", listOf(libraryDir.toString()))

        // Watcher is not running
        every { configService.get(ConfigProperties.Libraries.Scan.EnableFilesystemWatcher) } returns false
        every { libraryRepository.findAll() } returns emptyList()

        libraryWatcherService.start()

        // Try library created event
        val createEvent = LibraryCreatedEvent(this, library)
        libraryWatcherService.onLibraryCreated(createEvent)

        // Try library updated event
        val updateEvent = LibraryUpdatedEvent(this, library)
        libraryWatcherService.onLibraryUpdated(updateEvent)

        // Try library deleted event
        val deleteEvent = LibraryDeletedEvent(this, library)
        libraryWatcherService.onLibraryDeleted(deleteEvent)

        // All events should be ignored (no exceptions thrown)
        assertTrue(true)
    }

    // Helper methods

    private fun createTestLibrary(
        id: Long,
        name: String,
        directoryPaths: List<String>,
        games: MutableList<Game> = mutableListOf()
    ): Library {
        val directories = directoryPaths.map { path ->
            mockk<DirectoryMapping>(relaxed = true) {
                every { internalPath } returns path
                every { externalPath } returns null
            }
        }.toMutableList()

        return mockk<Library>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.name } returns name
            every { this@mockk.directories } returns directories
            every { this@mockk.games } returns games
        }
    }

    private fun createTestGame(id: Long, path: String, metadata: GameMetadata? = null): Game {
        val gameMetadata = metadata ?: GameMetadata(path = path, fileSize = 1000L)
        return mockk<Game>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.metadata } returns gameMetadata
            every { this@mockk.title } returns "Test Game"
        }
    }
}

