package org.gameyfin.app.games

import io.mockk.*
import org.gameyfin.app.core.plugins.dto.ExternalProviderIdDto
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.games.dto.*
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.libraries.LibraryCoreService
import org.gameyfin.app.libraries.LibraryService
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.nio.file.Path
import kotlin.test.assertEquals

class GameEndpointTest {

    private lateinit var gameService: GameService
    private lateinit var libraryService: LibraryService
    private lateinit var libraryCoreService: LibraryCoreService
    private lateinit var gameEndpoint: GameEndpoint

    @BeforeEach
    fun setup() {
        gameService = mockk()
        libraryService = mockk()
        libraryCoreService = mockk()
        gameEndpoint = GameEndpoint(gameService, libraryService, libraryCoreService)

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `subscribe should return admin flux when user is admin`() {
        mockkObject(GameService.Companion)
        every { isCurrentUserAdmin() } returns true
        val adminEvent: GameAdminEvent = GameAdminEvent.Created(mockk(relaxed = true))
        val adminFlux: Flux<List<GameAdminEvent>> = Flux.just(listOf(adminEvent))
        every { GameService.subscribeAdmin() } returns adminFlux

        val result = gameEndpoint.subscribe()

        assertEquals(adminFlux, result)
        unmockkObject(GameService.Companion)
    }

    @Test
    fun `subscribe should return user flux when user is not admin`() {
        mockkObject(GameService.Companion)
        every { isCurrentUserAdmin() } returns false
        val userEvent: GameUserEvent = GameUserEvent.Created(mockk(relaxed = true))
        val userFlux: Flux<List<GameUserEvent>> = Flux.just(listOf(userEvent))
        every { GameService.subscribeUser() } returns userFlux

        val result = gameEndpoint.subscribe()

        assertEquals(userFlux, result)
        unmockkObject(GameService.Companion)
    }

    @Suppress("ReactiveStreamsUnusedPublisher")
    @Test
    fun `subscribe should emit events to subscribers when admin`() {
        mockkObject(GameService.Companion)
        every { isCurrentUserAdmin() } returns true
        val event: GameAdminEvent = GameAdminEvent.Created(mockk<GameAdminDto>(relaxed = true))
        val adminFlux: Flux<List<GameAdminEvent>> = Flux.just(listOf(event))
        every { GameService.subscribeAdmin() } returns adminFlux

        val result = gameEndpoint.subscribe()

        StepVerifier.create(result)
            .expectNext(listOf(event))
            .verifyComplete()
        unmockkObject(GameService.Companion)
    }

    @Test
    fun `getAll should return all games from service`() {
        val games = listOf(mockk<GameDto>(relaxed = true), mockk<GameDto>(relaxed = true))
        every { gameService.getAll() } returns games

        val result = gameEndpoint.getAll()

        assertEquals(games, result)
        verify(exactly = 1) { gameService.getAll() }
    }

    @Test
    fun `getAll should return empty list when no games exist`() {
        every { gameService.getAll() } returns emptyList()

        val result = gameEndpoint.getAll()

        assertEquals(emptyList(), result)
        verify(exactly = 1) { gameService.getAll() }
    }

    @Test
    fun `getPotentialMatches should delegate to service with correct parameters`() {
        val searchTerm = "Test Game"
        val platformFilter = setOf(Platform.PC_MICROSOFT_WINDOWS, Platform.PLAYSTATION_5)
        val expectedResults = listOf(mockk<GameSearchResultDto>(relaxed = true))
        every { gameService.getPotentialMatches(searchTerm, platformFilter) } returns expectedResults

        val result = gameEndpoint.getPotentialMatches(searchTerm, platformFilter)

        assertEquals(expectedResults, result)
        verify(exactly = 1) { gameService.getPotentialMatches(searchTerm, platformFilter) }
    }

    @Test
    fun `getPotentialMatches should handle empty search term`() {
        val searchTerm = ""
        val platformFilter = setOf<Platform>()
        val expectedResults = emptyList<GameSearchResultDto>()
        every { gameService.getPotentialMatches(searchTerm, platformFilter) } returns expectedResults

        val result = gameEndpoint.getPotentialMatches(searchTerm, platformFilter)

        assertEquals(expectedResults, result)
        verify(exactly = 1) { gameService.getPotentialMatches(searchTerm, platformFilter) }
    }

    @Test
    fun `getPotentialMatches should handle empty platform filter`() {
        val searchTerm = "Test Game"
        val platformFilter = setOf<Platform>()
        val expectedResults = listOf(mockk<GameSearchResultDto>(relaxed = true))
        every { gameService.getPotentialMatches(searchTerm, platformFilter) } returns expectedResults

        val result = gameEndpoint.getPotentialMatches(searchTerm, platformFilter)

        assertEquals(expectedResults, result)
        verify(exactly = 1) { gameService.getPotentialMatches(searchTerm, platformFilter) }
    }

    @Test
    fun `updateGame should delegate to service`() {
        val gameUpdateDto = mockk<GameUpdateDto>()
        every { gameService.edit(gameUpdateDto) } just Runs

        gameEndpoint.updateGame(gameUpdateDto)

        verify(exactly = 1) { gameService.edit(gameUpdateDto) }
    }

    @Test
    fun `deleteGame should delete from library and service in correct order`() {
        val gameId = 123L
        every { libraryCoreService.deleteGameFromLibrary(gameId) } just Runs
        every { gameService.delete(gameId) } just Runs

        gameEndpoint.deleteGame(gameId)

        verifyOrder {
            libraryCoreService.deleteGameFromLibrary(gameId)
            gameService.delete(gameId)
        }
    }

    @Test
    fun `deleteGame should handle deletion of non-existent game`() {
        val gameId = 999L
        every { libraryCoreService.deleteGameFromLibrary(gameId) } just Runs
        every { gameService.delete(gameId) } just Runs

        gameEndpoint.deleteGame(gameId)

        verify(exactly = 1) { libraryCoreService.deleteGameFromLibrary(gameId) }
        verify(exactly = 1) { gameService.delete(gameId) }
    }

    @Test
    fun `matchManually should call service and add game to library when game is returned`() {
        val originalIds = mapOf("provider1" to ExternalProviderIdDto("plugin1", "ext123"))
        val path = "/test/game.exe"
        val libraryId = 1L
        val replaceGameId = null
        val library = mockk<Library>()
        val game = mockk<Game>()

        every { libraryService.getById(libraryId) } returns library
        every { gameService.matchManually(originalIds, Path.of(path), library, replaceGameId) } returns game
        coEvery { libraryCoreService.addGamesToLibrary(listOf(game), library, true) } returns library

        gameEndpoint.matchManually(originalIds, path, libraryId, replaceGameId)

        verify(exactly = 1) { libraryService.getById(libraryId) }
        verify(exactly = 1) { gameService.matchManually(originalIds, Path.of(path), library, replaceGameId) }
        coVerify(exactly = 1) { libraryCoreService.addGamesToLibrary(listOf(game), library, true) }
    }

    @Test
    fun `matchManually should not add to library when service returns null`() {
        val originalIds = mapOf("provider1" to ExternalProviderIdDto("plugin1", "ext123"))
        val path = "/test/game.exe"
        val libraryId = 1L
        val replaceGameId = null
        val library = mockk<Library>()

        every { libraryService.getById(libraryId) } returns library
        every { gameService.matchManually(originalIds, Path.of(path), library, replaceGameId) } returns null

        gameEndpoint.matchManually(originalIds, path, libraryId, replaceGameId)

        verify(exactly = 1) { libraryService.getById(libraryId) }
        verify(exactly = 1) { gameService.matchManually(originalIds, Path.of(path), library, replaceGameId) }
        coVerify(exactly = 0) { libraryCoreService.addGamesToLibrary(any(), any(), any()) }
    }

    @Test
    fun `matchManually should handle replace scenario`() {
        val originalIds = mapOf("provider1" to ExternalProviderIdDto("plugin1", "ext123"))
        val path = "/test/game.exe"
        val libraryId = 1L
        val replaceGameId = 5L
        val library = mockk<Library>()
        val game = mockk<Game>()

        every { libraryService.getById(libraryId) } returns library
        every { gameService.matchManually(originalIds, Path.of(path), library, replaceGameId) } returns game
        coEvery { libraryCoreService.addGamesToLibrary(listOf(game), library, true) } returns library

        gameEndpoint.matchManually(originalIds, path, libraryId, replaceGameId)

        verify(exactly = 1) { gameService.matchManually(originalIds, Path.of(path), library, replaceGameId) }
        coVerify(exactly = 1) { libraryCoreService.addGamesToLibrary(listOf(game), library, true) }
    }

    @Test
    fun `matchManually should handle multiple external IDs`() {
        val originalIds = mapOf(
            "provider1" to ExternalProviderIdDto("plugin1", "ext123"),
            "provider2" to ExternalProviderIdDto("plugin2", "ext456"),
            "provider3" to ExternalProviderIdDto("plugin3", "ext789")
        )
        val path = "/test/game.exe"
        val libraryId = 1L
        val replaceGameId = null
        val library = mockk<Library>()
        val game = mockk<Game>()

        every { libraryService.getById(libraryId) } returns library
        every { gameService.matchManually(originalIds, Path.of(path), library, replaceGameId) } returns game
        coEvery { libraryCoreService.addGamesToLibrary(listOf(game), library, true) } returns library

        gameEndpoint.matchManually(originalIds, path, libraryId, replaceGameId)

        verify(exactly = 1) { gameService.matchManually(originalIds, Path.of(path), library, replaceGameId) }
        coVerify(exactly = 1) { libraryCoreService.addGamesToLibrary(listOf(game), library, true) }
    }

    @Test
    fun `matchManually should handle Windows path format`() {
        val originalIds = mapOf("provider1" to ExternalProviderIdDto("plugin1", "ext123"))
        val path = "C:\\Games\\test game.exe"
        val libraryId = 1L
        val library = mockk<Library>()
        val game = mockk<Game>()

        every { libraryService.getById(libraryId) } returns library
        every { gameService.matchManually(any(), any(), library, null) } returns game
        coEvery { libraryCoreService.addGamesToLibrary(listOf(game), library, true) } returns library

        gameEndpoint.matchManually(originalIds, path, libraryId, null)

        verify(exactly = 1) { gameService.matchManually(originalIds, Path.of(path), library, null) }
    }

    @Test
    fun `matchManually should handle Unix path format`() {
        val originalIds = mapOf("provider1" to ExternalProviderIdDto("plugin1", "ext123"))
        val path = "/home/user/games/test game.sh"
        val libraryId = 1L
        val library = mockk<Library>()
        val game = mockk<Game>()

        every { libraryService.getById(libraryId) } returns library
        every { gameService.matchManually(any(), any(), library, null) } returns game
        coEvery { libraryCoreService.addGamesToLibrary(listOf(game), library, true) } returns library

        gameEndpoint.matchManually(originalIds, path, libraryId, null)

        verify(exactly = 1) { gameService.matchManually(originalIds, Path.of(path), library, null) }
    }
}
