package org.gameyfin.app.requests

import io.mockk.*
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.requests.dto.GameRequestCreationDto
import org.gameyfin.app.requests.dto.GameRequestDto
import org.gameyfin.app.requests.status.GameRequestStatus
import org.gameyfin.app.users.dto.UserInfoDto
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GameRequestEndpointTest {

    private lateinit var gameRequestService: GameRequestService
    private lateinit var config: ConfigService
    private lateinit var gameRequestEndpoint: GameRequestEndpoint

    @BeforeEach
    fun setup() {
        gameRequestService = mockk()
        config = mockk()
        gameRequestEndpoint = GameRequestEndpoint(gameRequestService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `subscribe should return non-null Flux from GameRequestService companion object`() {
        val result = gameRequestEndpoint.subscribe()
        assertNotNull(result)
    }

    @Test
    fun `getAll should return all game requests from service`() {
        val dto1 = createTestGameRequestDto(1L, "Game 1")
        val dto2 = createTestGameRequestDto(2L, "Game 2")
        val expectedList = listOf(dto1, dto2)

        every { gameRequestService.getAll() } returns expectedList

        val result = gameRequestEndpoint.getAll()

        assertEquals(expectedList, result)
        assertEquals(2, result.size)
        assertEquals("Game 1", result[0].title)
        assertEquals("Game 2", result[1].title)
        verify(exactly = 1) { gameRequestService.getAll() }
    }

    @Test
    fun `getAll should return empty list when no requests exist`() {
        every { gameRequestService.getAll() } returns emptyList()

        val result = gameRequestEndpoint.getAll()

        assertEquals(emptyList(), result)
        verify(exactly = 1) { gameRequestService.getAll() }
    }

    @Test
    fun `create should call service createRequest`() {
        val creationDto = GameRequestCreationDto(
            title = "New Game",
            platform = Platform.PC_MICROSOFT_WINDOWS,
            release = Instant.now()
        )

        every { gameRequestService.createRequest(creationDto) } just Runs

        gameRequestEndpoint.create(creationDto)

        verify(exactly = 1) { gameRequestService.createRequest(creationDto) }
    }

    @Test
    fun `create should handle different platforms`() {
        val platforms = listOf(
            Platform.PC_MICROSOFT_WINDOWS,
            Platform.PLAYSTATION_5,
            Platform.XBOX_SERIES_X_S,
            Platform.NINTENDO_SWITCH
        )

        platforms.forEach { platform ->
            val creationDto = GameRequestCreationDto(
                title = "Game for $platform",
                platform = platform,
                release = Instant.now()
            )

            every { gameRequestService.createRequest(creationDto) } just Runs

            gameRequestEndpoint.create(creationDto)
        }

        verify(exactly = platforms.size) { gameRequestService.createRequest(any()) }
    }

    @Test
    fun `create should handle null release date`() {
        val creationDto = GameRequestCreationDto(
            title = "Game Without Release",
            platform = Platform.PC_MICROSOFT_WINDOWS,
            release = null
        )

        every { gameRequestService.createRequest(creationDto) } just Runs

        gameRequestEndpoint.create(creationDto)

        verify(exactly = 1) { gameRequestService.createRequest(match { it.release == null }) }
    }

    @Test
    fun `toggleVote should call service toggleRequestVote`() {
        val gameRequestId = 42L

        every { gameRequestService.toggleRequestVote(gameRequestId) } just Runs

        gameRequestEndpoint.toggleVote(gameRequestId)

        verify(exactly = 1) { gameRequestService.toggleRequestVote(gameRequestId) }
    }

    @Test
    fun `toggleVote should handle multiple different IDs`() {
        val ids = listOf(1L, 2L, 3L, 100L, 999L)

        ids.forEach { id ->
            every { gameRequestService.toggleRequestVote(id) } just Runs
            gameRequestEndpoint.toggleVote(id)
        }

        verify(exactly = ids.size) { gameRequestService.toggleRequestVote(any()) }
    }

    @Test
    fun `delete should call service deleteRequest`() {
        val gameRequestId = 42L

        every { gameRequestService.deleteRequest(gameRequestId) } just Runs

        gameRequestEndpoint.delete(gameRequestId)

        verify(exactly = 1) { gameRequestService.deleteRequest(gameRequestId) }
    }

    @Test
    fun `delete should handle multiple different IDs`() {
        val ids = listOf(1L, 2L, 3L, 100L, 999L)

        ids.forEach { id ->
            every { gameRequestService.deleteRequest(id) } just Runs
            gameRequestEndpoint.delete(id)
        }

        verify(exactly = ids.size) { gameRequestService.deleteRequest(any()) }
    }

    @Test
    fun `changeStatus should call service changeRequestStatus`() {
        val gameRequestId = 42L
        val newStatus = GameRequestStatus.APPROVED

        every { gameRequestService.changeRequestStatus(gameRequestId, newStatus) } just Runs

        gameRequestEndpoint.changeStatus(gameRequestId, newStatus)

        verify(exactly = 1) { gameRequestService.changeRequestStatus(gameRequestId, newStatus) }
    }

    @Test
    fun `changeStatus should handle all status types`() {
        val gameRequestId = 1L
        val statuses = listOf(
            GameRequestStatus.PENDING,
            GameRequestStatus.APPROVED,
            GameRequestStatus.REJECTED,
            GameRequestStatus.FULFILLED
        )

        statuses.forEach { status ->
            every { gameRequestService.changeRequestStatus(gameRequestId, status) } just Runs
            gameRequestEndpoint.changeStatus(gameRequestId, status)
        }

        verify(exactly = statuses.size) { gameRequestService.changeRequestStatus(any(), any()) }
    }

    @Test
    fun `changeStatus should handle different request IDs with different statuses`() {
        every { gameRequestService.changeRequestStatus(1L, GameRequestStatus.APPROVED) } just Runs
        every { gameRequestService.changeRequestStatus(2L, GameRequestStatus.REJECTED) } just Runs
        every { gameRequestService.changeRequestStatus(3L, GameRequestStatus.FULFILLED) } just Runs

        gameRequestEndpoint.changeStatus(1L, GameRequestStatus.APPROVED)
        gameRequestEndpoint.changeStatus(2L, GameRequestStatus.REJECTED)
        gameRequestEndpoint.changeStatus(3L, GameRequestStatus.FULFILLED)

        verify(exactly = 1) { gameRequestService.changeRequestStatus(1L, GameRequestStatus.APPROVED) }
        verify(exactly = 1) { gameRequestService.changeRequestStatus(2L, GameRequestStatus.REJECTED) }
        verify(exactly = 1) { gameRequestService.changeRequestStatus(3L, GameRequestStatus.FULFILLED) }
    }

    private fun createTestGameRequestDto(
        id: Long,
        title: String,
        status: GameRequestStatus = GameRequestStatus.PENDING
    ): GameRequestDto {
        return GameRequestDto(
            id = id,
            title = title,
            release = Instant.parse("2024-01-01T00:00:00Z"),
            platform = Platform.PC_MICROSOFT_WINDOWS,
            status = status,
            requester = UserInfoDto(1L, "testuser", false, null),
            voters = emptyList(),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}

