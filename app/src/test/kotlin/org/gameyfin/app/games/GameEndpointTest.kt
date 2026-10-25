package org.gameyfin.app.games

import io.mockk.*
import org.gameyfin.app.core.plugins.dto.ExternalProviderIdDto
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.core.sync.EntityEventType
import org.gameyfin.app.games.dto.*
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.libraries.LibraryCoreService
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameEndpointTest {

    private lateinit var gameService: GameService
    private lateinit var libraryCoreService: LibraryCoreService
    private lateinit var gameEndpoint: GameEndpoint

    @BeforeEach
    fun setup() {
        gameService = mockk()
        libraryCoreService = mockk()
        gameEndpoint = GameEndpoint(gameService, libraryCoreService)

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `createGame adds manual game to library and returns id`() {
        val library = mockk<Library>()
        val game = mockk<Game> { every { id } returns 42L }
        every { libraryCoreService.getWithGamesAndIgnoredPaths(1L) } returns library
        every { gameService.createManually("Title", "/games/title", library) } returns game
        every { libraryCoreService.addGamesToLibrary(listOf(game), library, true) } returns library

        assertEquals(42L, gameEndpoint.createGame("Title", "/games/title", 1L))
        verify { libraryCoreService.addGamesToLibrary(listOf(game), library, true) }
    }

    @Test
    fun `subscribe should return admin flux when user is admin`() {
        mockkObject(GameService.Companion)
        every { isCurrentUserAdmin() } returns true
        val adminEvent = GameEvent(EntityEventType.DELETED, 1L)
        val adminFlux: Flux<List<GameEvent>> = Flux.just(listOf(adminEvent))
        every { GameService.subscribeAdmin() } returns adminFlux

        val result = gameEndpoint.subscribe()

        assertEquals(adminFlux, result)
        unmockkObject(GameService.Companion)
    }

    @Test
    fun `subscribe should return user flux when user is not admin`() {
        mockkObject(GameService.Companion)
        every { isCurrentUserAdmin() } returns false
        val userEvent = GameEvent(EntityEventType.DELETED, 1L)
        val userFlux: Flux<List<GameEvent>> = Flux.just(listOf(userEvent))
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
        val event = GameEvent(EntityEventType.CREATED, 1L, summary = mockk<GameSummaryDto>(relaxed = true))
        val adminFlux: Flux<List<GameEvent>> = Flux.just(listOf(event))
        every { GameService.subscribeAdmin() } returns adminFlux

        val result = gameEndpoint.subscribe()

        StepVerifier.create(result)
            .expectNext(listOf(event))
            .verifyComplete()
        unmockkObject(GameService.Companion)
    }

    @Test
    fun `sync should delegate to service with admin flag`() {
        val since = Instant.parse("2024-01-01T00:00:00Z")
        val syncDto = GameSyncDto(Instant.now(), false, emptyList(), listOf(1L, 2L))
        every { isCurrentUserAdmin() } returns true
        every { gameService.sync(since, true) } returns syncDto

        val result = gameEndpoint.sync(since)

        assertEquals(syncDto, result)
        verify(exactly = 1) { gameService.sync(since, true) }
    }

    @Test
    fun `sync should request full sync for non-admin users when since is null`() {
        val syncDto = GameSyncDto(Instant.now(), true, emptyList(), emptyList())
        every { isCurrentUserAdmin() } returns false
        every { gameService.sync(null, false) } returns syncDto

        val result = gameEndpoint.sync(null)

        assertEquals(syncDto, result)
        verify(exactly = 1) { gameService.sync(null, false) }
    }

    @Test
    fun `getById should return full game DTO from service`() {
        val dto = mockk<GameDto>(relaxed = true)
        every { gameService.getDtoById(42L) } returns dto

        assertEquals(dto, gameEndpoint.getById(42L))
    }

    @Test
    fun `getById should return null for unknown game`() {
        every { gameService.getDtoById(42L) } returns null

        assertNull(gameEndpoint.getById(42L))
    }

    @Test
    fun `getByIds should delegate to service`() {
        val dtos = listOf(mockk<GameDto>(relaxed = true))
        every { gameService.getDtosByIds(listOf(1L, 2L)) } returns dtos

        assertEquals(dtos, gameEndpoint.getByIds(listOf(1L, 2L)))
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

        every { libraryCoreService.getWithGamesAndIgnoredPaths(libraryId) } returns library
        every { gameService.matchManually(originalIds, Path.of(path), library, replaceGameId) } returns game
        coEvery { libraryCoreService.addGamesToLibrary(listOf(game), library, true) } returns library

        gameEndpoint.matchManually(originalIds, path, libraryId, replaceGameId)

        verify(exactly = 1) { libraryCoreService.getWithGamesAndIgnoredPaths(libraryId) }
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

        every { libraryCoreService.getWithGamesAndIgnoredPaths(libraryId) } returns library
        every { gameService.matchManually(originalIds, Path.of(path), library, replaceGameId) } returns null

        gameEndpoint.matchManually(originalIds, path, libraryId, replaceGameId)

        verify(exactly = 1) { libraryCoreService.getWithGamesAndIgnoredPaths(libraryId) }
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

        every { libraryCoreService.getWithGamesAndIgnoredPaths(libraryId) } returns library
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

        every { libraryCoreService.getWithGamesAndIgnoredPaths(libraryId) } returns library
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

        every { libraryCoreService.getWithGamesAndIgnoredPaths(libraryId) } returns library
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

        every { libraryCoreService.getWithGamesAndIgnoredPaths(libraryId) } returns library
        every { gameService.matchManually(any(), any(), library, null) } returns game
        coEvery { libraryCoreService.addGamesToLibrary(listOf(game), library, true) } returns library

        gameEndpoint.matchManually(originalIds, path, libraryId, null)

        verify(exactly = 1) { gameService.matchManually(originalIds, Path.of(path), library, null) }
    }

    @Test
    fun `getEnumPropertyValues should call service and return values`() {
        val enumValuesDto = mockk<GameEnumPropertyValuesDto>()
        every { gameService.getEnumPropertyValues() } returns enumValuesDto

        val result = gameEndpoint.getEnumPropertyValues()

        assertEquals(enumValuesDto, result)
        verify(exactly = 1) { gameService.getEnumPropertyValues() }
    }
}
