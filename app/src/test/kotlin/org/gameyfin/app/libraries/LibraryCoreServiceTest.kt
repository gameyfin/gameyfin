package org.gameyfin.app.libraries

import io.mockk.*
import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.libraries.entities.IgnoredPath
import org.gameyfin.app.libraries.entities.IgnoredPathPluginSource
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.users.UserService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryCoreServiceTest {

    private lateinit var libraryRepository: LibraryRepository
    private lateinit var gameService: GameService
    private lateinit var libraryCoreService: LibraryCoreService
    private lateinit var ignoredPathRepository: IgnoredPathRepository
    private lateinit var userService: UserService
    private lateinit var pluginService: PluginService

    @BeforeEach
    fun setup() {
        libraryRepository = mockk()
        gameService = mockk()
        ignoredPathRepository = mockk()
        userService = mockk()
        pluginService = mockk()

        libraryCoreService = LibraryCoreService(
            libraryRepository,
            gameService,
            ignoredPathRepository,
            userService,
            pluginService
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `addGamesToLibrary should add only new games`() {
        val existingGame = createTestGame(1L, "/path/game1")
        val newGame1 = createTestGame(2L, "/path/game2")
        val newGame2 = createTestGame(3L, "/path/game3")

        val library = createTestLibrary(games = mutableListOf(existingGame))
        val gamesToAdd = listOf(existingGame, newGame1, newGame2)

        val result = libraryCoreService.addGamesToLibrary(gamesToAdd, library, persist = false)

        assertEquals(3, result.games.size)
        assertTrue(result.games.contains(existingGame))
        assertTrue(result.games.contains(newGame1))
        assertTrue(result.games.contains(newGame2))
    }

    @Test
    fun `addGamesToLibrary should remove paths from unmatchedPaths when game is added`() {
        val game1 = createTestGame(1L, "/path/game1")
        val game2 = createTestGame(2L, "/path/game2")

        val library = createTestLibrary(
            ignoredPaths = mutableListOf(
                createTestIgnoredPath("/path/game1"),
                createTestIgnoredPath("/path/game2"),
                createTestIgnoredPath("/path/other")
            )
        )

        every { libraryRepository.save(library) } returns library

        val result = libraryCoreService.addGamesToLibrary(listOf(game1, game2), library, persist = false)

        assertEquals(1, result.ignoredPaths.size)
        assertTrue(result.ignoredPaths.map { it.path }.contains("/path/other"))
        assertFalse(result.ignoredPaths.map { it.path }.contains("/path/game1"))
        assertFalse(result.ignoredPaths.map { it.path }.contains("/path/game2"))
    }

    @Test
    fun `addGamesToLibrary should save library when unmatchedPaths were removed`() {
        val game = createTestGame(1L, "/path/game1")
        val library = createTestLibrary(
            ignoredPaths = mutableListOf(createTestIgnoredPath("/path/game1"))
        )

        every { libraryRepository.save(library) } returns library

        libraryCoreService.addGamesToLibrary(listOf(game), library, persist = false)

        verify(exactly = 1) { libraryRepository.save(library) }
    }

    @Test
    fun `addGamesToLibrary should save library when persist is true`() {
        val game = createTestGame(1L, "/path/game1")
        val library = createTestLibrary()

        every { libraryRepository.save(library) } returns library

        libraryCoreService.addGamesToLibrary(listOf(game), library, persist = true)

        verify(exactly = 1) { libraryRepository.save(library) }
    }

    @Test
    fun `addGamesToLibrary should not save library when no unmatchedPaths removed and persist is false`() {
        val game = createTestGame(1L, "/path/game1")
        val library = createTestLibrary()

        libraryCoreService.addGamesToLibrary(listOf(game), library, persist = false)

        verify(exactly = 0) { libraryRepository.save(any()) }
    }

    @Test
    fun `addGamesToLibrary should handle empty collection`() {
        val library = createTestLibrary()
        val initialGamesSize = library.games.size

        val result = libraryCoreService.addGamesToLibrary(emptyList(), library, persist = false)

        assertEquals(initialGamesSize, result.games.size)
    }

    @Test
    fun `addGamesToLibrary should not add duplicate games by ID`() {
        val existingGame = createTestGame(1L, "/path/game1")
        val duplicateGame = createTestGame(1L, "/path/game1_duplicate")

        val library = createTestLibrary(games = mutableListOf(existingGame))

        val result = libraryCoreService.addGamesToLibrary(listOf(duplicateGame), library, persist = false)

        assertEquals(1, result.games.size)
        assertEquals(existingGame, result.games[0])
    }

    @Test
    fun `deleteGameFromLibrary should remove game from library`() {
        val gameId = 1L
        val gamePath = "/path/game1"
        val library = createTestLibrary(games = mutableListOf())
        val game = createTestGame(gameId, gamePath, library)
        library.games.add(game)

        every { gameService.getById(gameId) } returns game
        every { libraryRepository.save(library) } returns library
        every { ignoredPathRepository.findByPath("/path/game1") } returns null
        every { pluginService.getPluginManagementEntries(any()) } returns emptyList()

        libraryCoreService.deleteGameFromLibrary(gameId)

        assertEquals(0, library.games.size)
        verify(exactly = 1) { libraryRepository.save(library) }
    }

    @Test
    fun `deleteGameFromLibrary should add path to ignoredPaths`() {
        val gameId = 1L
        val gamePath = "/path/game1"
        val library = createTestLibrary(games = mutableListOf())
        val game = createTestGame(gameId, gamePath, library)
        library.games.add(game)

        every { gameService.getById(gameId) } returns game
        every { libraryRepository.save(library) } returns library
        every { ignoredPathRepository.findByPath("/path/game1") } returns null
        every { pluginService.getPluginManagementEntries(any()) } returns emptyList()

        libraryCoreService.deleteGameFromLibrary(gameId)

        assertTrue(library.ignoredPaths.map { it.path }.contains(gamePath))
        verify(exactly = 1) { libraryRepository.save(library) }
    }

    @Test
    fun `deleteGameFromLibrary should remove only the specified game`() {
        val library = createTestLibrary(games = mutableListOf())
        val game1 = createTestGame(1L, "/path/game1", library)
        val game2 = createTestGame(2L, "/path/game2", library)
        library.games.add(game1)
        library.games.add(game2)

        every { gameService.getById(1L) } returns game1
        every { libraryRepository.save(library) } returns library
        every { ignoredPathRepository.findByPath("/path/game1") } returns null
        every { pluginService.getPluginManagementEntries(any()) } returns emptyList()

        libraryCoreService.deleteGameFromLibrary(1L)

        assertEquals(1, library.games.size)
        assertEquals(game2, library.games[0])
    }

    private fun createTestGame(id: Long, path: String, library: Library? = null): Game {
        val metadata = GameMetadata(path = path)
        return mockk<Game>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.metadata } returns metadata
            every { this@mockk.library } returns (library ?: mockk(relaxed = true))
        }
    }

    private fun createTestIgnoredPath(path: String): IgnoredPath {
        val pluginSource = IgnoredPathPluginSource(mutableListOf())
        return IgnoredPath(path = path, source = pluginSource)
    }

    private fun createTestLibrary(
        id: Long = 1L,
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
}