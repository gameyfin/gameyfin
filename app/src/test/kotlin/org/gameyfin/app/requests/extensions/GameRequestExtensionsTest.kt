package org.gameyfin.app.requests.extensions

import org.gameyfin.app.core.Role
import org.gameyfin.app.requests.entities.GameRequest
import org.gameyfin.app.requests.status.GameRequestStatus
import org.gameyfin.app.users.entities.User
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameRequestExtensionsTest {

    @Test
    fun `toDto should convert GameRequest to GameRequestDto with all fields`() {
        val user = createTestUser(1L, "testuser")
        val gameRequest = createTestGameRequest(1L, "Test Game", user)

        val dto = gameRequest.toDto()

        assertEquals(1L, dto.id)
        assertEquals("Test Game", dto.title)
        assertEquals(Platform.PC_MICROSOFT_WINDOWS, dto.platform)
        assertEquals(GameRequestStatus.PENDING, dto.status)
        assertNotNull(dto.requester)
        assertEquals("testuser", dto.requester.username)
        assertEquals(1L, dto.requester.id)
        assertNotNull(dto.createdAt)
        assertNotNull(dto.updatedAt)
        assertNotNull(dto.release)
    }

    @Test
    fun `toDto should handle null requester`() {
        val gameRequest = createTestGameRequest(1L, "Guest Game", null)

        val dto = gameRequest.toDto()

        assertEquals(1L, dto.id)
        assertEquals("Guest Game", dto.title)
        assertNull(dto.requester)
    }

    @Test
    fun `toDto should handle null release date`() {
        val gameRequest = createTestGameRequest(1L, "No Release Game", null, null)

        val dto = gameRequest.toDto()

        assertEquals(1L, dto.id)
        assertNull(dto.release)
    }

    @Test
    fun `toDto should convert all voters correctly`() {
        val user1 = createTestUser(1L, "user1")
        val user2 = createTestUser(2L, "user2")
        val user3 = createTestUser(3L, "user3")
        val gameRequest = createTestGameRequest(1L, "Popular Game", user1)
        gameRequest.voters = mutableSetOf(user1, user2, user3)

        val dto = gameRequest.toDto()

        assertEquals(3, dto.voters.size)
        val usernames = dto.voters.map { it.username }
        assertEquals(true, usernames.contains("user1"))
        assertEquals(true, usernames.contains("user2"))
        assertEquals(true, usernames.contains("user3"))
    }

    @Test
    fun `toDto should handle empty voters list`() {
        val gameRequest = createTestGameRequest(1L, "No Votes Game")
        gameRequest.voters = mutableSetOf()

        val dto = gameRequest.toDto()

        assertEquals(0, dto.voters.size)
    }

    @Test
    fun `toDto should handle different statuses`() {
        val statuses = listOf(
            GameRequestStatus.PENDING,
            GameRequestStatus.APPROVED,
            GameRequestStatus.REJECTED,
            GameRequestStatus.FULFILLED
        )

        statuses.forEachIndexed { index, status ->
            val gameRequest = createTestGameRequest(index.toLong(), "Game $index")
            gameRequest.status = status

            val dto = gameRequest.toDto()

            assertEquals(status, dto.status)
        }
    }

    @Test
    fun `toDto should handle different platforms`() {
        val platforms = listOf(
            Platform.PC_MICROSOFT_WINDOWS,
            Platform.PLAYSTATION_5,
            Platform.XBOX_SERIES_X_S,
            Platform.NINTENDO_SWITCH
        )

        platforms.forEachIndexed { index, platform ->
            val gameRequest = GameRequest(
                id = index.toLong(),
                title = "Game $index",
                release = Instant.parse("2024-01-01T00:00:00Z"),
                platform = platform,
                status = GameRequestStatus.PENDING,
                requester = null,
                voters = mutableSetOf(),
                linkedGameId = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            val dto = gameRequest.toDto()

            assertEquals(platform, dto.platform)
        }
    }

    @Test
    fun `toDto should preserve exact timestamps`() {
        val createdAt = Instant.parse("2024-01-01T10:00:00Z")
        val updatedAt = Instant.parse("2024-01-02T15:30:00Z")
        val gameRequest = createTestGameRequest(1L, "Timestamped Game")
        gameRequest.createdAt = createdAt
        gameRequest.updatedAt = updatedAt

        val dto = gameRequest.toDto()

        assertEquals(createdAt, dto.createdAt)
        assertEquals(updatedAt, dto.updatedAt)
    }

    @Test
    fun `toDtos should convert empty collection to empty list`() {
        val emptyCollection = emptyList<GameRequest>()

        val dtos = emptyCollection.toDtos()

        assertEquals(0, dtos.size)
    }

    @Test
    fun `toDtos should convert single item collection`() {
        val gameRequest = createTestGameRequest(1L, "Single Game")
        val collection = listOf(gameRequest)

        val dtos = collection.toDtos()

        assertEquals(1, dtos.size)
        assertEquals(1L, dtos[0].id)
        assertEquals("Single Game", dtos[0].title)
    }

    @Test
    fun `toDtos should convert multiple items collection`() {
        val user = createTestUser(1L, "testuser")
        val request1 = createTestGameRequest(1L, "Game 1", user)
        val request2 = createTestGameRequest(2L, "Game 2", null)
        val request3 = createTestGameRequest(3L, "Game 3", user)
        val collection = listOf(request1, request2, request3)

        val dtos = collection.toDtos()

        assertEquals(3, dtos.size)
        assertEquals(1L, dtos[0].id)
        assertEquals("Game 1", dtos[0].title)
        assertEquals(2L, dtos[1].id)
        assertEquals("Game 2", dtos[1].title)
        assertEquals(3L, dtos[2].id)
        assertEquals("Game 3", dtos[2].title)
    }

    @Test
    fun `toDtos should preserve order of collection`() {
        val requests = (1..10).map { createTestGameRequest(it.toLong(), "Game $it") }

        val dtos = requests.toDtos()

        assertEquals(10, dtos.size)
        dtos.forEachIndexed { index, dto ->
            assertEquals((index + 1).toLong(), dto.id)
            assertEquals("Game ${index + 1}", dto.title)
        }
    }

    @Test
    fun `toDtos should work with Set collection`() {
        val request1 = createTestGameRequest(1L, "Game 1")
        val request2 = createTestGameRequest(2L, "Game 2")
        val collection: Set<GameRequest> = setOf(request1, request2)

        val dtos = collection.toDtos()

        assertEquals(2, dtos.size)
    }

    @Test
    fun `toDtos should handle mixed requesters in collection`() {
        val user1 = createTestUser(1L, "user1")
        val user2 = createTestUser(2L, "user2")
        val request1 = createTestGameRequest(1L, "Game 1", user1)
        val request2 = createTestGameRequest(2L, "Game 2", null)
        val request3 = createTestGameRequest(3L, "Game 3", user2)
        val collection = listOf(request1, request2, request3)

        val dtos = collection.toDtos()

        assertEquals(3, dtos.size)
        assertEquals("user1", dtos[0].requester?.username)
        assertNull(dtos[1].requester)
        assertEquals("user2", dtos[2].requester?.username)
    }

    @Test
    fun `toDtos should handle different statuses in collection`() {
        val request1 = createTestGameRequest(1L, "Game 1")
        request1.status = GameRequestStatus.PENDING
        val request2 = createTestGameRequest(2L, "Game 2")
        request2.status = GameRequestStatus.APPROVED
        val request3 = createTestGameRequest(3L, "Game 3")
        request3.status = GameRequestStatus.FULFILLED
        val collection = listOf(request1, request2, request3)

        val dtos = collection.toDtos()

        assertEquals(GameRequestStatus.PENDING, dtos[0].status)
        assertEquals(GameRequestStatus.APPROVED, dtos[1].status)
        assertEquals(GameRequestStatus.FULFILLED, dtos[2].status)
    }

    @Test
    fun `toDtos should handle different platforms in collection`() {
        val request1 = GameRequest(
            id = 1L,
            title = "Game 1",
            release = Instant.parse("2024-01-01T00:00:00Z"),
            platform = Platform.PC_MICROSOFT_WINDOWS,
            status = GameRequestStatus.PENDING,
            requester = null,
            voters = mutableSetOf(),
            linkedGameId = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val request2 = GameRequest(
            id = 2L,
            title = "Game 2",
            release = Instant.parse("2024-01-01T00:00:00Z"),
            platform = Platform.PLAYSTATION_5,
            status = GameRequestStatus.PENDING,
            requester = null,
            voters = mutableSetOf(),
            linkedGameId = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val request3 = GameRequest(
            id = 3L,
            title = "Game 3",
            release = Instant.parse("2024-01-01T00:00:00Z"),
            platform = Platform.NINTENDO_SWITCH,
            status = GameRequestStatus.PENDING,
            requester = null,
            voters = mutableSetOf(),
            linkedGameId = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val collection = listOf(request1, request2, request3)

        val dtos = collection.toDtos()

        assertEquals(Platform.PC_MICROSOFT_WINDOWS, dtos[0].platform)
        assertEquals(Platform.PLAYSTATION_5, dtos[1].platform)
        assertEquals(Platform.NINTENDO_SWITCH, dtos[2].platform)
    }

    @Test
    fun `toDto should handle requests with voters of different sizes`() {
        val user1 = createTestUser(1L, "user1")
        val user2 = createTestUser(2L, "user2")
        val user3 = createTestUser(3L, "user3")
        val user4 = createTestUser(4L, "user4")
        val user5 = createTestUser(5L, "user5")

        val gameRequest1 = createTestGameRequest(1L, "Game 1", user1)
        gameRequest1.voters = mutableSetOf(user1)

        val gameRequest2 = createTestGameRequest(2L, "Game 2", user2)
        gameRequest2.voters = mutableSetOf(user2, user3, user4, user5)

        val dto1 = gameRequest1.toDto()
        val dto2 = gameRequest2.toDto()

        assertEquals(1, dto1.voters.size)
        assertEquals(4, dto2.voters.size)
    }

    private fun createTestGameRequest(
        id: Long,
        title: String,
        requester: User? = null,
        release: Instant? = Instant.parse("2024-01-01T00:00:00Z")
    ): GameRequest {
        return GameRequest(
            id = id,
            title = title,
            release = release,
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

