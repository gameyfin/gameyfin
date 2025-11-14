package org.gameyfin.app.requests.entities

import io.mockk.*
import org.gameyfin.app.core.Role
import org.gameyfin.app.requests.GameRequestService
import org.gameyfin.app.requests.dto.GameRequestEvent
import org.gameyfin.app.requests.status.GameRequestStatus
import org.gameyfin.app.users.entities.User
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class GameRequestEntityListenerTest {

    private lateinit var listener: GameRequestEntityListener

    @BeforeEach
    fun setup() {
        listener = GameRequestEntityListener()
        mockkObject(GameRequestService.Companion)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `created should emit Created event`() {
        val gameRequest = createTestGameRequest(1L, "New Game")
        val capturedEvents = mutableListOf<GameRequestEvent>()

        every { GameRequestService.emit(capture(capturedEvents)) } just Runs

        listener.created(gameRequest)

        assertEquals(1, capturedEvents.size)
        val event = capturedEvents[0]
        assertEquals(GameRequestEvent.Created::class, event::class)
        assertEquals("created", event.type)
        val createdEvent = event as GameRequestEvent.Created
        assertEquals(1L, createdEvent.gameRequest.id)
        assertEquals("New Game", createdEvent.gameRequest.title)
        verify(exactly = 1) { GameRequestService.emit(any<GameRequestEvent.Created>()) }
    }

    @Test
    fun `created should emit event with correct DTO properties`() {
        val user = createTestUser(1L, "testuser")
        val gameRequest = createTestGameRequest(2L, "Test Game", user)
        val capturedEvents = mutableListOf<GameRequestEvent>()

        every { GameRequestService.emit(capture(capturedEvents)) } just Runs

        listener.created(gameRequest)

        val event = capturedEvents[0] as GameRequestEvent.Created
        assertEquals(2L, event.gameRequest.id)
        assertEquals("Test Game", event.gameRequest.title)
        assertEquals(Platform.PC_MICROSOFT_WINDOWS, event.gameRequest.platform)
        assertEquals(GameRequestStatus.PENDING, event.gameRequest.status)
        assertEquals("testuser", event.gameRequest.requester?.username)
    }

    @Test
    fun `updated should emit Updated event`() {
        val gameRequest = createTestGameRequest(1L, "Updated Game")
        val capturedEvents = mutableListOf<GameRequestEvent>()

        every { GameRequestService.emit(capture(capturedEvents)) } just Runs

        listener.updated(gameRequest)

        assertEquals(1, capturedEvents.size)
        val event = capturedEvents[0]
        assertEquals(GameRequestEvent.Updated::class, event::class)
        assertEquals("updated", event.type)
        val updatedEvent = event as GameRequestEvent.Updated
        assertEquals(1L, updatedEvent.gameRequest.id)
        assertEquals("Updated Game", updatedEvent.gameRequest.title)
        verify(exactly = 1) { GameRequestService.emit(any<GameRequestEvent.Updated>()) }
    }

    @Test
    fun `updated should emit event with correct status`() {
        val gameRequest = createTestGameRequest(1L, "Game")
        gameRequest.status = GameRequestStatus.APPROVED
        val capturedEvents = mutableListOf<GameRequestEvent>()

        every { GameRequestService.emit(capture(capturedEvents)) } just Runs

        listener.updated(gameRequest)

        val event = capturedEvents[0] as GameRequestEvent.Updated
        assertEquals(GameRequestStatus.APPROVED, event.gameRequest.status)
    }

    @Test
    fun `deleted should emit Deleted event`() {
        val gameRequest = createTestGameRequest(42L, "Deleted Game")
        val capturedEvents = mutableListOf<GameRequestEvent>()

        every { GameRequestService.emit(capture(capturedEvents)) } just Runs

        listener.deleted(gameRequest)

        assertEquals(1, capturedEvents.size)
        val event = capturedEvents[0]
        assertEquals(GameRequestEvent.Deleted::class, event::class)
        assertEquals("deleted", event.type)
        val deletedEvent = event as GameRequestEvent.Deleted
        assertEquals(42L, deletedEvent.gameRequestId)
        verify(exactly = 1) { GameRequestService.emit(any<GameRequestEvent.Deleted>()) }
    }

    @Test
    fun `created should handle guest requester`() {
        val gameRequest = createTestGameRequest(1L, "Guest Game", null)
        val capturedEvents = mutableListOf<GameRequestEvent>()

        every { GameRequestService.emit(capture(capturedEvents)) } just Runs

        listener.created(gameRequest)

        val event = capturedEvents[0] as GameRequestEvent.Created
        assertEquals(null, event.gameRequest.requester)
    }

    @Test
    fun `updated should handle multiple voters`() {
        val user1 = createTestUser(1L, "user1")
        val user2 = createTestUser(2L, "user2")
        val user3 = createTestUser(3L, "user3")
        val gameRequest = createTestGameRequest(1L, "Popular Game", user1)
        gameRequest.voters = mutableSetOf(user1, user2, user3)
        val capturedEvents = mutableListOf<GameRequestEvent>()

        every { GameRequestService.emit(capture(capturedEvents)) } just Runs

        listener.updated(gameRequest)

        val event = capturedEvents[0] as GameRequestEvent.Updated
        assertEquals(3, event.gameRequest.voters.size)
    }

    @Test
    fun `created should handle null release date`() {
        val gameRequest = GameRequest(
            id = 1L,
            title = "No Release Game",
            release = null,
            platform = Platform.PC_MICROSOFT_WINDOWS,
            status = GameRequestStatus.PENDING,
            requester = null,
            voters = mutableSetOf(),
            linkedGameId = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val capturedEvents = mutableListOf<GameRequestEvent>()

        every { GameRequestService.emit(capture(capturedEvents)) } just Runs

        listener.created(gameRequest)

        val event = capturedEvents[0] as GameRequestEvent.Created
        assertEquals(null, event.gameRequest.release)
    }

    @Test
    fun `updated should handle fulfilled status with linked game`() {
        val gameRequest = createTestGameRequest(1L, "Fulfilled Game")
        gameRequest.status = GameRequestStatus.FULFILLED
        gameRequest.linkedGameId = 123L
        val capturedEvents = mutableListOf<GameRequestEvent>()

        every { GameRequestService.emit(capture(capturedEvents)) } just Runs

        listener.updated(gameRequest)

        val event = capturedEvents[0] as GameRequestEvent.Updated
        assertEquals(GameRequestStatus.FULFILLED, event.gameRequest.status)
    }

    @Test
    fun `all lifecycle methods should handle different request IDs`() {
        val capturedEvents = mutableListOf<GameRequestEvent>()
        every { GameRequestService.emit(capture(capturedEvents)) } just Runs

        listener.created(createTestGameRequest(1L, "Game 1"))
        listener.updated(createTestGameRequest(2L, "Game 2"))
        listener.deleted(createTestGameRequest(3L, "Game 3"))

        assertEquals(3, capturedEvents.size)
        assertEquals(GameRequestEvent.Created::class, capturedEvents[0]::class)
        assertEquals(GameRequestEvent.Updated::class, capturedEvents[1]::class)
        assertEquals(GameRequestEvent.Deleted::class, capturedEvents[2]::class)
    }

    private fun createTestGameRequest(
        id: Long,
        title: String,
        requester: User? = null
    ): GameRequest {
        return GameRequest(
            id = id,
            title = title,
            release = Instant.parse("2024-01-01T00:00:00Z"),
            platform = Platform.PC_MICROSOFT_WINDOWS,
            status = GameRequestStatus.PENDING,
            requester = requester,
            voters = mutableSetOf(),
            linkedGameId = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    private fun createTestUser(id: Long, username: String): User {
        return User(
            id = id,
            username = username,
            email = "$username@example.com",
            password = "password",
            enabled = true,
            emailConfirmed = true,
            roles = listOf(Role.USER)
        )
    }
}

