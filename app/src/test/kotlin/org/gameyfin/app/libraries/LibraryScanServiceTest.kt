package org.gameyfin.app.libraries

import io.mockk.*
import org.gameyfin.app.core.filesystem.FilesystemScanResult
import org.gameyfin.app.core.filesystem.FilesystemService
import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.core.plugins.dto.PluginDto
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.entities.IgnoredPath
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.libraries.enums.ScanType
import org.gameyfin.app.libraries.scan.LibraryGameProcessor
import org.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pf4j.PluginState
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.Path

class LibraryScanServiceTest {

    private lateinit var libraryRepository: LibraryRepository
    private lateinit var filesystemService: FilesystemService
    private lateinit var libraryCoreService: LibraryCoreService
    private lateinit var libraryGameProcessor: LibraryGameProcessor
    private lateinit var gameRepository: GameRepository
    private lateinit var libraryScanService: LibraryScanService
    private lateinit var ignoredPathRepository: IgnoredPathRepository
    private lateinit var pluginService: PluginService

    @BeforeEach
    fun setup() {
        libraryRepository = mockk()
        filesystemService = mockk()
        libraryCoreService = mockk()
        libraryGameProcessor = mockk()
        gameRepository = mockk()
        ignoredPathRepository = mockk()
        pluginService = mockk()

        // By default, at least one GameMetadataProvider is started so scans are allowed
        every { pluginService.getAllByTypeAndState(GameMetadataProvider::class, PluginState.STARTED) } returns listOf(
            mockk<PluginDto>()
        )

        libraryScanService = LibraryScanService(
            libraryRepository,
            filesystemService,
            libraryCoreService,
            libraryGameProcessor,
            gameRepository,
            ignoredPathRepository,
            pluginService
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `triggerScan should scan all libraries when libraryIds is null`() {
        val library1 = createTestLibrary(1L)
        val library2 = createTestLibrary(2L)

        every { libraryRepository.findAll() } returns listOf(library1, library2)
        setupSuccessfulQuickScan(library1)
        setupSuccessfulQuickScan(library2)

        libraryScanService.triggerScan(ScanType.QUICK, null)

        Thread.sleep(100)
        verify(atLeast = 1) { filesystemService.scanLibraryForGamefiles(any()) }
    }

    @Test
    fun `triggerScan should scan only specified libraries`() {
        val library1 = createTestLibrary(1L)
        val library2 = createTestLibrary(2L)

        every { libraryRepository.findAllById(listOf(1L)) } returns listOf(library1)
        setupSuccessfulQuickScan(library1)

        libraryScanService.triggerScan(ScanType.QUICK, listOf(1L))

        Thread.sleep(100)
        verify(atLeast = 1) { filesystemService.scanLibraryForGamefiles(library1) }
        verify(exactly = 0) { filesystemService.scanLibraryForGamefiles(library2) }
    }

    @Test
    fun `triggerScan should not start duplicate scan for same library`() {
        val library = createTestLibrary(1L)

        every { libraryRepository.findAllById(listOf(1L)) } returns listOf(library)
        setupDelayedQuickScan(library)

        libraryScanService.triggerScan(ScanType.QUICK, listOf(1L))
        Thread.sleep(50)
        libraryScanService.triggerScan(ScanType.QUICK, listOf(1L))

        Thread.sleep(200)
        verify(exactly = 1) { filesystemService.scanLibraryForGamefiles(library) }
    }

    @Test
    fun `triggerScan should handle quick scan type`() {
        val library = createTestLibrary(1L)

        every { libraryRepository.findAllById(listOf(1L)) } returns listOf(library)
        setupSuccessfulQuickScan(library)

        libraryScanService.triggerScan(ScanType.QUICK, listOf(1L))

        Thread.sleep(100)
        verify(atLeast = 1) { filesystemService.scanLibraryForGamefiles(library) }
    }

    @Test
    fun `triggerScan should handle full scan type`() {
        val library = createTestLibrary(1L)

        every { libraryRepository.findAllById(listOf(1L)) } returns listOf(library)
        setupSuccessfulFullScan(library)

        libraryScanService.triggerScan(ScanType.FULL, listOf(1L))

        Thread.sleep(100)
        verify(atLeast = 1) { filesystemService.scanLibraryForGamefiles(library) }
    }

    @Test
    fun `triggerScan should handle scheduled scan type`() {
        val library = createTestLibrary(1L)

        every { libraryRepository.findAllById(listOf(1L)) } returns listOf(library)
        setupSuccessfulFullScan(library)

        libraryScanService.triggerScan(ScanType.SCHEDULED, listOf(1L))

        Thread.sleep(100)
        verify(atLeast = 1) { filesystemService.scanLibraryForGamefiles(library) }
    }

    @Test
    fun `triggerScan should handle empty library list`() {
        every { libraryRepository.findAll() } returns emptyList()

        libraryScanService.triggerScan(ScanType.QUICK, null)

        Thread.sleep(50)
        verify(exactly = 0) { filesystemService.scanLibraryForGamefiles(any()) }
    }

    @Test
    fun `triggerScan should throw when no GameMetadataProvider plugin is started`() {
        every {
            pluginService.getAllByTypeAndState(
                GameMetadataProvider::class,
                PluginState.STARTED
            )
        } returns emptyList()

        assertThrows<IllegalStateException> {
            libraryScanService.triggerScan(ScanType.QUICK, null)
        }

        verify(exactly = 0) { libraryRepository.findAll() }
        verify(exactly = 0) { filesystemService.scanLibraryForGamefiles(any()) }
    }

    @Test
    fun `triggerScan should handle filesystem scan errors`() {
        val library = createTestLibrary(1L)

        every { libraryRepository.findAllById(listOf(1L)) } returns listOf(library)
        every { filesystemService.scanLibraryForGamefiles(library) } throws RuntimeException("Scan error")

        libraryScanService.triggerScan(ScanType.QUICK, listOf(1L))

        Thread.sleep(100)
        verify(atLeast = 1) { filesystemService.scanLibraryForGamefiles(library) }
    }

    @Test
    fun `quick scan should process new games`() {
        val library = createTestLibrary(1L)
        val newPath = Path("/path/newgame")
        val newGame = createTestGame(1L, newPath.toString())

        every { libraryRepository.findAllById(listOf(1L)) } returns listOf(library)
        setupQuickScanWithNewGames(library, listOf(newPath), newGame)

        libraryScanService.triggerScan(ScanType.QUICK, listOf(1L))

        Thread.sleep(200)
        verify(atLeast = 1) { libraryGameProcessor.processNewGame(newPath, library) }
    }

    @Test
    fun `quick scan should handle unmatched games`() {
        val library = createTestLibrary(1L)
        val unmatchedPath = Path("/path/unmatched")

        every { libraryRepository.findAllById(listOf(1L)) } returns listOf(library)
        setupQuickScanWithUnmatchedGames(library, listOf(unmatchedPath))

        libraryScanService.triggerScan(ScanType.QUICK, listOf(1L))

        Thread.sleep(200)
        verify(atLeast = 1) { libraryGameProcessor.processNewGame(unmatchedPath, library) }
    }

    @Test
    fun `full scan should update existing games`() {
        val existingGame = createTestGame(1L, "/path/game1")
        val library = createTestLibrary(1L, games = mutableListOf(existingGame))

        every { libraryRepository.findAllById(listOf(1L)) } returns listOf(library)
        setupFullScanWithExistingGames(library, existingGame)

        libraryScanService.triggerScan(ScanType.FULL, listOf(1L))

        Thread.sleep(200)
        verify(atLeast = 1) { libraryGameProcessor.processExistingGame(existingGame) }
    }

    @Test
    fun `scan should remove deleted games from library`() {
        val removedPath = "/path/removed"
        val removedGame = createTestGame(1L, removedPath)
        val library = createTestLibrary(1L, games = mutableListOf(removedGame))

        every { libraryRepository.findAllById(listOf(1L)) } returns listOf(library)
        setupQuickScanWithRemovedGames(library, listOf(Path(removedPath)))

        libraryScanService.triggerScan(ScanType.QUICK, listOf(1L))

        Thread.sleep(200)
        verify(atLeast = 1) { filesystemService.scanLibraryForGamefiles(library) }
    }

    @Test
    fun `scan should update library timestamp after completion`() {
        val library = createTestLibrary(1L)

        every { libraryRepository.findAllById(listOf(1L)) } returns listOf(library)
        setupSuccessfulQuickScan(library)

        libraryScanService.triggerScan(ScanType.QUICK, listOf(1L))

        Thread.sleep(200)
        verify(atLeast = 1) { libraryRepository.save(library) }
    }

    @Test
    fun `scan should add new games to library after processing`() {
        val library = createTestLibrary(1L)
        val newPath = Path("/path/newgame")
        val newGame = createTestGame(1L, newPath.toString())

        every { libraryRepository.findAllById(listOf(1L)) } returns listOf(library)
        setupQuickScanWithNewGames(library, listOf(newPath), newGame)
        every { gameRepository.findAllById(listOf(1L)) } returns listOf(newGame)

        libraryScanService.triggerScan(ScanType.QUICK, listOf(1L))

        Thread.sleep(200)
        verify(atLeast = 1) { libraryCoreService.addGamesToLibrary(any(), library, false) }
    }

    private fun createTestLibrary(
        id: Long,
        games: MutableList<Game> = mutableListOf(),
        ignoredPaths: MutableList<IgnoredPath> = mutableListOf()
    ): Library {
        var updatedAtTime = Instant.now()
        return mockk<Library>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.games } returns games
            every { this@mockk.ignoredPaths } returns ignoredPaths
            every { this@mockk.updatedAt } answers { updatedAtTime }
            every { this@mockk.updatedAt = any() } propertyType Instant::class answers {
                updatedAtTime = value
            }
        }
    }

    private fun createTestGame(id: Long, path: String): Game {
        val metadata = GameMetadata(path = path)
        return mockk<Game>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.metadata } returns metadata
        }
    }

    private fun setupSuccessfulQuickScan(library: Library) {
        every { filesystemService.scanLibraryForGamefiles(library) } returns FilesystemScanResult(
            newPaths = emptyList(),
            removedGamePaths = emptyList(),
            removedIgnoredPaths = emptyList()
        )
        every { libraryRepository.save(library) } returns library
        every { gameRepository.findAllById(emptyList<Long>()) } returns emptyList()
        every { libraryCoreService.addGamesToLibrary(any(), library, false) } returns library
    }

    private fun setupDelayedQuickScan(library: Library) {
        every { filesystemService.scanLibraryForGamefiles(library) } answers {
            Thread.sleep(150)
            FilesystemScanResult(
                newPaths = emptyList(),
                removedGamePaths = emptyList(),
                removedIgnoredPaths = emptyList()
            )
        }
        every { libraryRepository.save(library) } returns library
        every { gameRepository.findAllById(emptyList<Long>()) } returns emptyList()
        every { libraryCoreService.addGamesToLibrary(any(), library, false) } returns library
    }

    private fun setupSuccessfulFullScan(library: Library) {
        every { filesystemService.scanLibraryForGamefiles(library) } returns FilesystemScanResult(
            newPaths = emptyList(),
            removedGamePaths = emptyList(),
            removedIgnoredPaths = emptyList()
        )
        every { libraryGameProcessor.processExistingGame(any()) } returns null
        every { libraryRepository.save(library) } returns library
        every { gameRepository.findAllById(emptyList<Long>()) } returns emptyList()
        every { libraryCoreService.addGamesToLibrary(any(), library, false) } returns library
    }

    private fun setupQuickScanWithNewGames(library: Library, newPaths: List<Path>, newGame: Game) {
        every { filesystemService.scanLibraryForGamefiles(library) } returns FilesystemScanResult(
            newPaths = newPaths,
            removedGamePaths = emptyList(),
            removedIgnoredPaths = emptyList()
        )
        every { libraryGameProcessor.processNewGame(any(), library) } returns newGame
        every { gameRepository.findAllById(listOf(newGame.id!!)) } returns listOf(newGame)
        every { libraryCoreService.addGamesToLibrary(listOf(newGame), library, false) } returns library
        every { libraryRepository.save(library) } returns library
    }

    private fun setupQuickScanWithUnmatchedGames(library: Library, unmatchedPaths: List<Path>) {
        every { filesystemService.scanLibraryForGamefiles(library) } returns FilesystemScanResult(
            newPaths = unmatchedPaths,
            removedGamePaths = emptyList(),
            removedIgnoredPaths = emptyList()
        )
        every { libraryGameProcessor.processNewGame(any(), library) } throws IllegalStateException("Could not match")
        every { gameRepository.findAllById(emptyList<Long>()) } returns emptyList()
        every { libraryCoreService.addGamesToLibrary(emptyList(), library, false) } returns library
        every { libraryRepository.save(library) } returns library
    }

    private fun setupFullScanWithExistingGames(library: Library, existingGame: Game) {
        every { filesystemService.scanLibraryForGamefiles(library) } returns FilesystemScanResult(
            newPaths = emptyList(),
            removedGamePaths = emptyList(),
            removedIgnoredPaths = emptyList()
        )
        every { libraryGameProcessor.processExistingGame(existingGame) } returns existingGame
        every { gameRepository.findAllById(emptyList<Long>()) } returns emptyList()
        every { libraryCoreService.addGamesToLibrary(emptyList(), library, false) } returns library
        every { libraryRepository.save(library) } returns library
    }

    private fun setupQuickScanWithRemovedGames(library: Library, removedPaths: List<Path>) {
        every { filesystemService.scanLibraryForGamefiles(library) } returns FilesystemScanResult(
            newPaths = emptyList(),
            removedGamePaths = removedPaths,
            removedIgnoredPaths = emptyList()
        )
        every { gameRepository.findAllById(emptyList<Long>()) } returns emptyList()
        every { libraryCoreService.addGamesToLibrary(emptyList(), library, false) } returns library
        every { libraryRepository.save(library) } returns library
    }
}


