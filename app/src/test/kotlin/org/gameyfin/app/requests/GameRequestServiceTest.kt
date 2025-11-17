package org.gameyfin.app.requests

import com.vaadin.hilla.exception.EndpointException
import io.mockk.*
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.events.GameCreatedEvent
import org.gameyfin.app.core.events.GameUpdatedEvent
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.requests.dto.GameRequestCreationDto
import org.gameyfin.app.requests.dto.GameRequestEvent
import org.gameyfin.app.requests.entities.GameRequest
import org.gameyfin.app.requests.status.GameRequestStatus
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.entities.User
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameRequestServiceTest {

    private lateinit var config: ConfigService
    private lateinit var userService: UserService
    private lateinit var gameRequestRepository: GameRequestRepository
    private lateinit var gameRepository: GameRepository
    private lateinit var gameRequestService: GameRequestService
    private lateinit var securityContext: SecurityContext
    private lateinit var authentication: Authentication

    @BeforeEach
    fun setup() {
        config = mockk()
        userService = mockk()
        gameRequestRepository = mockk()
        gameRepository = mockk()

        gameRequestService = GameRequestService(config, userService, gameRequestRepository, gameRepository)

        securityContext = mockk()
        authentication = mockk()
        mockkStatic(SecurityContextHolder::class)
        every { SecurityContextHolder.getContext() } returns securityContext
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `getAll should return all game requests as DTOs`() {
        val request1 = createTestGameRequest(1L, "Game 1")
        val request2 = createTestGameRequest(2L, "Game 2")
        val requests = listOf(request1, request2)

        every { gameRequestRepository.findAll() } returns requests

        val result = gameRequestService.getAll()

        assertEquals(2, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("Game 1", result[0].title)
        assertEquals(2L, result[1].id)
        assertEquals("Game 2", result[1].title)
        verify(exactly = 1) { gameRequestRepository.findAll() }
    }

    @Test
    fun `getAll should return empty list when no requests exist`() {
        every { gameRequestRepository.findAll() } returns emptyList()

        val result = gameRequestService.getAll()

        assertEquals(0, result.size)
        verify(exactly = 1) { gameRequestRepository.findAll() }
    }

    @Test
    fun `createRequest should throw exception when requests are disabled`() {
        val creationDto = GameRequestCreationDto(
            title = "Test Game",
            platform = Platform.PC_MICROSOFT_WINDOWS,
            release = Instant.now()
        )

        every { config.get(ConfigProperties.Requests.Games.Enabled) } returns false

        val exception = assertThrows(EndpointException::class.java) {
            gameRequestService.createRequest(creationDto)
        }

        assertEquals("Game requests are disabled", exception.message)
        verify(exactly = 1) { config.get(ConfigProperties.Requests.Games.Enabled) }
        verify(exactly = 0) { gameRequestRepository.save(any()) }
    }

    @Test
    fun `createRequest should throw exception when game already exists`() {
        val release = Instant.parse("2024-01-15T00:00:00Z")
        val creationDto = GameRequestCreationDto(
            title = "Existing Game",
            platform = Platform.PC_MICROSOFT_WINDOWS,
            release = release
        )
        val existingGame = createTestGame(1L, "Existing Game")

        every { config.get(ConfigProperties.Requests.Games.Enabled) } returns true
        every {
            gameRepository.findByTitleAndReleaseYearAndPlatform(
                "Existing Game",
                release,
                Platform.PC_MICROSOFT_WINDOWS
            )
        } returns listOf(existingGame)

        val exception = assertThrows(EndpointException::class.java) {
            gameRequestService.createRequest(creationDto)
        }

        assertTrue(exception.message!!.contains("This game is already available"))
        assertTrue(exception.message!!.contains("ID: 1"))
        verify(exactly = 0) { gameRequestRepository.save(any()) }
    }

    @Test
    fun `createRequest should throw exception when request already exists`() {
        val release = Instant.parse("2024-01-15T00:00:00Z")
        val creationDto = GameRequestCreationDto(
            title = "Requested Game",
            platform = Platform.PC_MICROSOFT_WINDOWS,
            release = release
        )
        val existingRequest = createTestGameRequest(1L, "Requested Game")

        every { config.get(ConfigProperties.Requests.Games.Enabled) } returns true
        every {
            gameRepository.findByTitleAndReleaseYearAndPlatform(
                "Requested Game",
                release,
                Platform.PC_MICROSOFT_WINDOWS
            )
        } returns emptyList()
        every {
            gameRequestRepository.findByTitleAndReleaseYearAndPlatform(
                "Requested Game",
                release,
                Platform.PC_MICROSOFT_WINDOWS
            )
        } returns listOf(existingRequest)

        val exception = assertThrows(EndpointException::class.java) {
            gameRequestService.createRequest(creationDto)
        }

        assertTrue(exception.message!!.contains("A request for this game"))
        assertTrue(exception.message!!.contains("already exists"))
        assertTrue(exception.message!!.contains("ID: 1"))
        verify(exactly = 0) { gameRequestRepository.save(any()) }
    }

    @Test
    fun `createRequest should throw exception when guest tries to request and guests not allowed`() {
        val creationDto = GameRequestCreationDto(
            title = "New Game",
            platform = Platform.PC_MICROSOFT_WINDOWS,
            release = Instant.now()
        )

        every { config.get(ConfigProperties.Requests.Games.Enabled) } returns true
        every { gameRepository.findByTitleAndReleaseYearAndPlatform(any(), any(), any()) } returns emptyList()
        every { gameRequestRepository.findByTitleAndReleaseYearAndPlatform(any(), any(), any()) } returns emptyList()
        every { securityContext.authentication } returns null
        every { config.get(ConfigProperties.Requests.Games.AllowGuestsToRequestGames) } returns false

        val exception = assertThrows(EndpointException::class.java) {
            gameRequestService.createRequest(creationDto)
        }

        assertEquals("Only registered users can submit game requests", exception.message)
        verify(exactly = 0) { gameRequestRepository.save(any()) }
    }

    @Test
    fun `createRequest should throw exception when user exceeds max pending requests`() {
        val user = createTestUser(1L, "testuser")
        val creationDto = GameRequestCreationDto(
            title = "New Game",
            platform = Platform.PC_MICROSOFT_WINDOWS,
            release = Instant.now()
        )
        val existingRequest1 = createTestGameRequest(1L, "Game 1", user)
        val existingRequest2 = createTestGameRequest(2L, "Game 2", user)

        setupAuthentication("testuser", false)
        every { config.get(ConfigProperties.Requests.Games.Enabled) } returns true
        every { gameRepository.findByTitleAndReleaseYearAndPlatform(any(), any(), any()) } returns emptyList()
        every { gameRequestRepository.findByTitleAndReleaseYearAndPlatform(any(), any(), any()) } returns emptyList()
        every { userService.getByUsername("testuser") } returns user
        every { config.get(ConfigProperties.Requests.Games.AllowGuestsToRequestGames) } returns true
        every {
            gameRequestRepository.findRequestsByRequesterIdAndStatusIn(
                1L,
                listOf(GameRequestStatus.PENDING)
            )
        } returns listOf(existingRequest1, existingRequest2)
        every { config.get(ConfigProperties.Requests.Games.MaxOpenRequestsPerUser) } returns 2

        val exception = assertThrows(EndpointException::class.java) {
            gameRequestService.createRequest(creationDto)
        }

        assertTrue(exception.message!!.contains("maximum number of pending requests"))
        assertTrue(exception.message!!.contains("2"))
        verify(exactly = 0) { gameRequestRepository.save(any()) }
    }

    @Test
    fun `createRequest should succeed for admin even when exceeding max requests`() {
        val admin = createTestUser(1L, "admin")
        val release = Instant.parse("2024-01-15T00:00:00Z")
        val creationDto = GameRequestCreationDto(
            title = "New Game",
            platform = Platform.PC_MICROSOFT_WINDOWS,
            release = release
        )
        val existingRequest1 = createTestGameRequest(2L, "Game 1", admin)
        val existingRequest2 = createTestGameRequest(3L, "Game 2", admin)

        setupAuthentication("admin", true)
        every { config.get(ConfigProperties.Requests.Games.Enabled) } returns true
        every { config.get(ConfigProperties.Requests.Games.AllowGuestsToRequestGames) } returns false
        every { gameRepository.findByTitleAndReleaseYearAndPlatform(any(), any(), any()) } returns emptyList()
        every { gameRequestRepository.findByTitleAndReleaseYearAndPlatform(any(), any(), any()) } returns emptyList()
        every { userService.getByUsername("admin") } returns admin
        every {
            gameRequestRepository.findRequestsByRequesterIdAndStatusIn(
                1L,
                listOf(GameRequestStatus.PENDING)
            )
        } returns listOf(existingRequest1, existingRequest2)
        every { config.get(ConfigProperties.Requests.Games.MaxOpenRequestsPerUser) } returns 2
        every { gameRequestRepository.save(any()) } answers { firstArg() }

        gameRequestService.createRequest(creationDto)

        verify(exactly = 1) {
            gameRequestRepository.save(match {
                it.title == "New Game" && it.requester == admin && it.voters.contains(admin)
            })
        }
    }

    @Test
    fun `createRequest should succeed with unlimited requests when max is 0`() {
        val user = createTestUser(1L, "testuser")
        val release = Instant.parse("2024-01-15T00:00:00Z")
        val creationDto = GameRequestCreationDto(
            title = "New Game",
            platform = Platform.PC_MICROSOFT_WINDOWS,
            release = release
        )
        val existingRequests = (1..10).map { createTestGameRequest(it.toLong(), "Game $it", user) }

        setupAuthentication("testuser", false)
        every { config.get(ConfigProperties.Requests.Games.Enabled) } returns true
        every { gameRepository.findByTitleAndReleaseYearAndPlatform(any(), any(), any()) } returns emptyList()
        every { gameRequestRepository.findByTitleAndReleaseYearAndPlatform(any(), any(), any()) } returns emptyList()
        every { userService.getByUsername("testuser") } returns user
        every { config.get(ConfigProperties.Requests.Games.AllowGuestsToRequestGames) } returns true
        every {
            gameRequestRepository.findRequestsByRequesterIdAndStatusIn(
                1L,
                listOf(GameRequestStatus.PENDING)
            )
        } returns existingRequests
        every { config.get(ConfigProperties.Requests.Games.MaxOpenRequestsPerUser) } returns 0
        every { gameRequestRepository.save(any()) } answers { firstArg() }

        gameRequestService.createRequest(creationDto)

        verify(exactly = 1) { gameRequestRepository.save(any()) }
    }

    @Test
    fun `createRequest should create request for authenticated user`() {
        val user = createTestUser(1L, "testuser")
        val release = Instant.parse("2024-01-15T00:00:00Z")
        val creationDto = GameRequestCreationDto(
            title = "New Game",
            platform = Platform.PC_MICROSOFT_WINDOWS,
            release = release
        )

        setupAuthentication("testuser", false)
        every { config.get(ConfigProperties.Requests.Games.Enabled) } returns true
        every { gameRepository.findByTitleAndReleaseYearAndPlatform(any(), any(), any()) } returns emptyList()
        every { gameRequestRepository.findByTitleAndReleaseYearAndPlatform(any(), any(), any()) } returns emptyList()
        every { userService.getByUsername("testuser") } returns user
        every { config.get(ConfigProperties.Requests.Games.AllowGuestsToRequestGames) } returns true
        every {
            gameRequestRepository.findRequestsByRequesterIdAndStatusIn(
                1L,
                listOf(GameRequestStatus.PENDING)
            )
        } returns emptyList()
        every { config.get(ConfigProperties.Requests.Games.MaxOpenRequestsPerUser) } returns 5
        every { gameRequestRepository.save(any()) } answers { firstArg() }

        gameRequestService.createRequest(creationDto)

        verify(exactly = 1) {
            gameRequestRepository.save(match {
                it.title == "New Game" &&
                        it.platform == Platform.PC_MICROSOFT_WINDOWS &&
                        it.release?.toEpochMilli() == release.toEpochMilli() &&
                        it.status == GameRequestStatus.PENDING &&
                        it.requester == user &&
                        it.voters.contains(user)
            })
        }
    }

    @Test
    fun `createRequest should create request for guest when allowed`() {
        val release = Instant.parse("2024-01-15T00:00:00Z")
        val creationDto = GameRequestCreationDto(
            title = "New Game",
            platform = Platform.PLAYSTATION_5,
            release = release
        )

        every { securityContext.authentication } returns null
        every { config.get(ConfigProperties.Requests.Games.Enabled) } returns true
        every { gameRepository.findByTitleAndReleaseYearAndPlatform(any(), any(), any()) } returns emptyList()
        every { gameRequestRepository.findByTitleAndReleaseYearAndPlatform(any(), any(), any()) } returns emptyList()
        every { config.get(ConfigProperties.Requests.Games.AllowGuestsToRequestGames) } returns true
        every {
            gameRequestRepository.findRequestsByRequesterIdAndStatusIn(
                null,
                listOf(GameRequestStatus.PENDING)
            )
        } returns emptyList()
        every { config.get(ConfigProperties.Requests.Games.MaxOpenRequestsPerUser) } returns 5
        every { gameRequestRepository.save(any()) } answers { firstArg() }

        gameRequestService.createRequest(creationDto)

        verify(exactly = 1) {
            gameRequestRepository.save(match {
                it.title == "New Game" &&
                        it.platform == Platform.PLAYSTATION_5 &&
                        it.requester == null &&
                        it.voters.isEmpty()
            })
        }
    }

    @Test
    fun `createRequest should handle null release date`() {
        val user = createTestUser(1L, "testuser")
        val creationDto = GameRequestCreationDto(
            title = "New Game",
            platform = Platform.XBOX_SERIES_X_S,
            release = null
        )

        setupAuthentication("testuser", false)
        every { config.get(ConfigProperties.Requests.Games.Enabled) } returns true
        every {
            gameRepository.findByTitleAndReleaseYearAndPlatform(
                "New Game",
                null,
                Platform.XBOX_SERIES_X_S
            )
        } returns emptyList()
        every {
            gameRequestRepository.findByTitleAndReleaseYearAndPlatform(
                "New Game",
                null,
                Platform.XBOX_SERIES_X_S
            )
        } returns emptyList()
        every { userService.getByUsername("testuser") } returns user
        every { config.get(ConfigProperties.Requests.Games.AllowGuestsToRequestGames) } returns true
        every {
            gameRequestRepository.findRequestsByRequesterIdAndStatusIn(
                1L,
                listOf(GameRequestStatus.PENDING)
            )
        } returns emptyList()
        every { config.get(ConfigProperties.Requests.Games.MaxOpenRequestsPerUser) } returns 5
        every { gameRequestRepository.save(any()) } answers { firstArg() }

        gameRequestService.createRequest(creationDto)

        verify(exactly = 1) { gameRequestRepository.save(match { it.release == null }) }
    }

    @Test
    fun `deleteRequest should throw exception when request not found`() {
        every { gameRequestRepository.findById(999L) } returns Optional.empty()

        assertThrows(NoSuchElementException::class.java) {
            gameRequestService.deleteRequest(999L)
        }

        verify(exactly = 0) { gameRequestRepository.delete(any()) }
    }

    @Test
    fun `deleteRequest should allow requester to delete their own request`() {
        val user = createTestUser(1L, "testuser")
        val request = createTestGameRequest(1L, "Game", user)

        setupAuthentication("testuser", false)
        every { gameRequestRepository.findById(1L) } returns Optional.of(request)
        every { userService.getByUsername("testuser") } returns user
        every { gameRequestRepository.delete(request) } just Runs

        gameRequestService.deleteRequest(1L)

        verify(exactly = 1) { gameRequestRepository.delete(request) }
    }

    @Test
    fun `deleteRequest should allow admin to delete any request`() {
        val user = createTestUser(1L, "testuser")
        val admin = createTestUser(2L, "admin")
        val request = createTestGameRequest(1L, "Game", user)

        setupAuthentication("admin", true)
        every { gameRequestRepository.findById(1L) } returns Optional.of(request)
        every { userService.getByUsername("admin") } returns admin
        every { gameRequestRepository.delete(request) } just Runs

        gameRequestService.deleteRequest(1L)

        verify(exactly = 1) { gameRequestRepository.delete(request) }
    }

    @Test
    fun `deleteRequest should throw exception when non-admin user tries to delete others request`() {
        val user1 = createTestUser(1L, "user1")
        val user2 = createTestUser(2L, "user2")
        val request = createTestGameRequest(1L, "Game", user1)

        setupAuthentication("user2", false)
        every { gameRequestRepository.findById(1L) } returns Optional.of(request)
        every { userService.getByUsername("user2") } returns user2

        val exception = assertThrows(EndpointException::class.java) {
            gameRequestService.deleteRequest(1L)
        }

        assertEquals("Only the requester or an admin can delete a game request", exception.message)
        verify(exactly = 0) { gameRequestRepository.delete(any()) }
    }

    @Test
    fun `deleteRequest should throw exception when non-admin tries to delete guest request`() {
        val user = createTestUser(1L, "testuser")
        val request = createTestGameRequest(1L, "Game", null)

        setupAuthentication("testuser", false)
        every { gameRequestRepository.findById(1L) } returns Optional.of(request)
        every { userService.getByUsername("testuser") } returns user

        val exception = assertThrows(EndpointException::class.java) {
            gameRequestService.deleteRequest(1L)
        }

        assertEquals("Only the requester or an admin can delete a game request", exception.message)
        verify(exactly = 0) { gameRequestRepository.delete(any()) }
    }

    @Test
    fun `deleteRequest should allow admin to delete guest request`() {
        val admin = createTestUser(1L, "admin")
        val request = createTestGameRequest(1L, "Game", null)

        setupAuthentication("admin", true)
        every { gameRequestRepository.findById(1L) } returns Optional.of(request)
        every { userService.getByUsername("admin") } returns admin
        every { gameRequestRepository.delete(request) } just Runs

        gameRequestService.deleteRequest(1L)

        verify(exactly = 1) { gameRequestRepository.delete(request) }
    }

    @Test
    fun `changeRequestStatus should update status when valid`() {
        val request = createTestGameRequest(1L, "Game")

        every { gameRequestRepository.findById(1L) } returns Optional.of(request)
        every { gameRequestRepository.save(any()) } answers { firstArg() }

        gameRequestService.changeRequestStatus(1L, GameRequestStatus.APPROVED)

        assertEquals(GameRequestStatus.APPROVED, request.status)
        verify(exactly = 1) { gameRequestRepository.save(request) }
    }

    @Test
    fun `changeRequestStatus should not update when status is FULFILLED`() {
        val request = createTestGameRequest(1L, "Game")
        request.status = GameRequestStatus.FULFILLED

        every { gameRequestRepository.findById(1L) } returns Optional.of(request)

        gameRequestService.changeRequestStatus(1L, GameRequestStatus.APPROVED)

        assertEquals(GameRequestStatus.FULFILLED, request.status)
        verify(exactly = 0) { gameRequestRepository.save(any()) }
    }

    @Test
    fun `changeRequestStatus should throw exception when request not found`() {
        every { gameRequestRepository.findById(999L) } returns Optional.empty()

        assertThrows(NoSuchElementException::class.java) {
            gameRequestService.changeRequestStatus(999L, GameRequestStatus.APPROVED)
        }

        verify(exactly = 0) { gameRequestRepository.save(any()) }
    }

    @Test
    fun `changeRequestStatus should handle all status transitions`() {
        val statuses = listOf(
            GameRequestStatus.PENDING,
            GameRequestStatus.APPROVED,
            GameRequestStatus.REJECTED,
            GameRequestStatus.FULFILLED
        )

        statuses.forEach { newStatus ->
            val request = createTestGameRequest(1L, "Game")
            every { gameRequestRepository.findById(1L) } returns Optional.of(request)
            every { gameRequestRepository.save(any()) } answers { firstArg() }

            gameRequestService.changeRequestStatus(1L, newStatus)

            assertEquals(newStatus, request.status)
        }

        verify(exactly = statuses.size) { gameRequestRepository.save(any()) }
    }

    @Test
    fun `toggleRequestVote should add user to voters when not present`() {
        val user = createTestUser(1L, "testuser")
        val request = createTestGameRequest(1L, "Game")
        request.voters = mutableSetOf()

        setupAuthentication("testuser", false)
        every { userService.getByUsername("testuser") } returns user
        every { gameRequestRepository.findById(1L) } returns Optional.of(request)
        every { gameRequestRepository.save(any()) } answers { firstArg() }

        gameRequestService.toggleRequestVote(1L)

        assertTrue(request.voters.contains(user))
        verify(exactly = 1) { gameRequestRepository.save(request) }
    }

    @Test
    fun `toggleRequestVote should remove user from voters when present`() {
        val user = createTestUser(1L, "testuser")
        val request = createTestGameRequest(1L, "Game")
        request.voters = mutableSetOf(user)

        setupAuthentication("testuser", false)
        every { userService.getByUsername("testuser") } returns user
        every { gameRequestRepository.findById(1L) } returns Optional.of(request)
        every { gameRequestRepository.save(any()) } answers { firstArg() }

        gameRequestService.toggleRequestVote(1L)

        assertTrue(!request.voters.contains(user))
        verify(exactly = 1) { gameRequestRepository.save(request) }
    }

    @Test
    fun `toggleRequestVote should throw exception when not authenticated`() {
        every { securityContext.authentication } returns null

        assertThrows(IllegalStateException::class.java) {
            gameRequestService.toggleRequestVote(1L)
        }

        verify(exactly = 0) { gameRequestRepository.save(any()) }
    }

    @Test
    fun `toggleRequestVote should throw exception when user not found`() {
        setupAuthentication("testuser", false)
        every { userService.getByUsername("testuser") } returns null

        assertThrows(IllegalStateException::class.java) {
            gameRequestService.toggleRequestVote(1L)
        }

        verify(exactly = 0) { gameRequestRepository.save(any()) }
    }

    @Test
    fun `toggleRequestVote should throw exception when request not found`() {
        val user = createTestUser(1L, "testuser")

        setupAuthentication("testuser", false)
        every { userService.getByUsername("testuser") } returns user
        every { gameRequestRepository.findById(999L) } returns Optional.empty()

        assertThrows(NoSuchElementException::class.java) {
            gameRequestService.toggleRequestVote(999L)
        }

        verify(exactly = 0) { gameRequestRepository.save(any()) }
    }

    @Test
    fun `onGameCreated should complete matching requests`() {
        val game = createTestGame(1L, "Test Game")
        game.platforms = listOf(Platform.PC_MICROSOFT_WINDOWS, Platform.PLAYSTATION_5)
        val request1 = createTestGameRequest(1L, "Test Game")
        request1.status = GameRequestStatus.PENDING
        val request2 = createTestGameRequest(2L, "Test Game")
        request2.status = GameRequestStatus.APPROVED

        mockkObject(GameRequestService.Companion)
        every { GameRequestService.emit(any()) } just Runs
        every {
            gameRequestRepository.findRequestsByTitleAndReleaseYearAndPlatformAndStatusNotIn(
                "Test Game", game.release, Platform.PC_MICROSOFT_WINDOWS, listOf(GameRequestStatus.FULFILLED)
            )
        } returns listOf(request1, request2)
        every {
            gameRequestRepository.findRequestsByTitleAndReleaseYearAndPlatformAndStatusNotIn(
                "Test Game", game.release, Platform.PLAYSTATION_5, listOf(GameRequestStatus.FULFILLED)
            )
        } returns emptyList()
        every { gameRequestRepository.save(any()) } answers { firstArg() }

        gameRequestService.onGameCreated(GameCreatedEvent(game = game, source = this))

        assertEquals(GameRequestStatus.FULFILLED, request1.status)
        assertEquals(1L, request1.linkedGameId)
        assertEquals(GameRequestStatus.FULFILLED, request2.status)
        assertEquals(1L, request2.linkedGameId)
        verify(exactly = 2) { gameRequestRepository.save(any()) }
        verify(exactly = 2) { GameRequestService.emit(any<GameRequestEvent.Updated>()) }
        unmockkObject(GameRequestService.Companion)
    }

    @Test
    fun `onGameCreated should not complete requests when game title is null`() {
        val game = createTestGame(1L, "Test Game")
        game.title = null

        gameRequestService.onGameCreated(GameCreatedEvent(game = game, source = this))

        verify(exactly = 0) {
            gameRequestRepository.findRequestsByTitleAndReleaseYearAndPlatformAndStatusNotIn(
                any(),
                any(),
                any(),
                any()
            )
        }
        verify(exactly = 0) { gameRequestRepository.save(any()) }
    }

    @Test
    fun `onGameCreated should handle multiple platforms`() {
        val game = createTestGame(1L, "Multi Platform Game")
        game.platforms = listOf(Platform.PC_MICROSOFT_WINDOWS, Platform.PLAYSTATION_5, Platform.XBOX_SERIES_X_S)
        val request1 = createTestGameRequest(1L, "Multi Platform Game")
        val request2 = createTestGameRequest(2L, "Multi Platform Game")
        val request3 = createTestGameRequest(3L, "Multi Platform Game")

        mockkObject(GameRequestService.Companion)
        every { GameRequestService.emit(any()) } just Runs
        every {
            gameRequestRepository.findRequestsByTitleAndReleaseYearAndPlatformAndStatusNotIn(
                "Multi Platform Game", game.release, Platform.PC_MICROSOFT_WINDOWS, listOf(GameRequestStatus.FULFILLED)
            )
        } returns listOf(request1)
        every {
            gameRequestRepository.findRequestsByTitleAndReleaseYearAndPlatformAndStatusNotIn(
                "Multi Platform Game", game.release, Platform.PLAYSTATION_5, listOf(GameRequestStatus.FULFILLED)
            )
        } returns listOf(request2)
        every {
            gameRequestRepository.findRequestsByTitleAndReleaseYearAndPlatformAndStatusNotIn(
                "Multi Platform Game", game.release, Platform.XBOX_SERIES_X_S, listOf(GameRequestStatus.FULFILLED)
            )
        } returns listOf(request3)
        every { gameRequestRepository.save(any()) } answers { firstArg() }

        gameRequestService.onGameCreated(GameCreatedEvent(this, game))

        assertEquals(GameRequestStatus.FULFILLED, request1.status)
        assertEquals(GameRequestStatus.FULFILLED, request2.status)
        assertEquals(GameRequestStatus.FULFILLED, request3.status)
        verify(exactly = 3) { gameRequestRepository.save(any()) }
        unmockkObject(GameRequestService.Companion)
    }

    @Test
    fun `onGameUpdated should complete matching requests`() {
        val game = createTestGame(1L, "Updated Game")
        game.platforms = listOf(Platform.NINTENDO_SWITCH)
        val request = createTestGameRequest(1L, "Updated Game")
        request.status = GameRequestStatus.PENDING

        mockkObject(GameRequestService.Companion)
        every { GameRequestService.emit(any()) } just Runs
        every {
            gameRequestRepository.findRequestsByTitleAndReleaseYearAndPlatformAndStatusNotIn(
                "Updated Game", game.release, Platform.NINTENDO_SWITCH, listOf(GameRequestStatus.FULFILLED)
            )
        } returns listOf(request)
        every { gameRequestRepository.save(any()) } answers { firstArg() }

        gameRequestService.onGameUpdated(GameUpdatedEvent(this, game, game))

        assertEquals(GameRequestStatus.FULFILLED, request.status)
        assertEquals(1L, request.linkedGameId)
        verify(exactly = 1) { gameRequestRepository.save(request) }
        unmockkObject(GameRequestService.Companion)
    }

    @Test
    fun `onGameCreated should not update already fulfilled requests`() {
        val game = createTestGame(1L, "Test Game")
        game.platforms = listOf(Platform.PC_MICROSOFT_WINDOWS)
        val request = createTestGameRequest(1L, "Test Game")
        request.status = GameRequestStatus.FULFILLED

        every {
            gameRequestRepository.findRequestsByTitleAndReleaseYearAndPlatformAndStatusNotIn(
                "Test Game", game.release, Platform.PC_MICROSOFT_WINDOWS, listOf(GameRequestStatus.FULFILLED)
            )
        } returns emptyList()

        gameRequestService.onGameCreated(GameCreatedEvent(this, game))

        verify(exactly = 0) { gameRequestRepository.save(any()) }
    }

    @Test
    fun `subscribe should return non-null Flux`() {
        val result = GameRequestService.subscribe()
        assertNotNull(result)
    }

    private fun createTestGameRequest(
        id: Long,
        title: String = "Test Game",
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

    private fun createTestGame(id: Long, title: String): Game {
        return Game(
            id = id,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            library = mockk(relaxed = true),
            title = title,
            platforms = listOf(Platform.PC_MICROSOFT_WINDOWS),
            coverImage = null,
            headerImage = null,
            comment = null,
            summary = null,
            release = Instant.parse("2024-01-01T00:00:00Z"),
            userRating = 0,
            criticRating = 0,
            publishers = mutableListOf(),
            developers = mutableListOf(),
            genres = emptyList(),
            themes = emptyList(),
            keywords = emptyList(),
            features = emptyList(),
            perspectives = emptyList(),
            images = mutableListOf(),
            videoUrls = emptyList(),
            metadata = GameMetadata(
                path = "/test/path",
                fileSize = 1000L,
                fields = mutableMapOf(),
                originalIds = emptyMap(),
                downloadCount = 0,
                matchConfirmed = false
            )
        )
    }

    private fun setupAuthentication(username: String, isAdmin: Boolean) {
        val authorities: Collection<GrantedAuthority> = if (isAdmin) {
            listOf(SimpleGrantedAuthority("ROLE_ADMIN"))
        } else {
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        }

        val userDetails = mockk<UserDetails> {
            every { getUsername() } returns username
            every { getAuthorities() } returns authorities
        }

        every { securityContext.authentication } returns authentication
        every { authentication.principal } returns userDetails
        every { authentication.name } returns username
        every { authentication.authorities } returns authorities
    }
}

