package org.gameyfin.app.users.emailconfirmation

import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import org.gameyfin.app.core.events.EmailNeedsConfirmationEvent
import org.gameyfin.app.core.token.Token
import org.gameyfin.app.core.token.TokenRepository
import org.gameyfin.app.core.token.TokenType
import org.gameyfin.app.core.token.TokenValidationResult
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.persistence.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailConfirmationServiceTest {

    private lateinit var tokenRepository: TokenRepository
    private lateinit var userRepository: UserRepository
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var emailConfirmationService: EmailConfirmationService

    @BeforeEach
    fun setup() {
        tokenRepository = mockk()
        userRepository = mockk()
        eventPublisher = mockk()

        emailConfirmationService = EmailConfirmationService(
            tokenRepository,
            userRepository,
            eventPublisher
        )

        every { eventPublisher.publishEvent(any()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    private fun mockRequestContext() {
        val request = mockk<HttpServletRequest>()
        val requestAttributes = mockk<ServletRequestAttributes>()

        every { request.scheme } returns "http"
        every { request.serverName } returns "localhost"
        every { request.serverPort } returns 8080
        every { requestAttributes.request } returns request

        mockkStatic(RequestContextHolder::class)
        every { RequestContextHolder.getRequestAttributes() } returns requestAttributes
    }

    @Test
    fun `confirmEmail should return VALID and confirm email for valid token`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = false
        )
        val token = Token(
            secret = "secret123",
            creator = user,
            type = TokenType.EmailConfirmation,
            createdOn = Instant.now()
        )

        every { tokenRepository.findBySecret("validSecret") } returns token
        every { userRepository.save(user) } returns user
        every { tokenRepository.delete(token) } just Runs

        val result = emailConfirmationService.confirmEmail("validSecret")

        assertEquals(TokenValidationResult.VALID, result)
        assertTrue(user.emailConfirmed)
        verify(exactly = 1) { userRepository.save(user) }
        verify(exactly = 1) { tokenRepository.delete(token) }
    }

    @Test
    fun `confirmEmail should return INVALID when token not found`() {
        every { tokenRepository.findBySecret("invalidSecret") } returns null

        val result = emailConfirmationService.confirmEmail("invalidSecret")

        assertEquals(TokenValidationResult.INVALID, result)
        verify(exactly = 0) { userRepository.save(any()) }
        verify(exactly = 0) { tokenRepository.delete(any()) }
    }

    @Test
    fun `confirmEmail should delete token after successful confirmation`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = false
        )
        val token = Token(
            secret = "secret123",
            creator = user,
            type = TokenType.EmailConfirmation,
            createdOn = Instant.now()
        )

        every { tokenRepository.findBySecret("validSecret") } returns token
        every { userRepository.save(user) } returns user
        every { tokenRepository.delete(token) } just Runs

        emailConfirmationService.confirmEmail("validSecret")

        verify(exactly = 1) { tokenRepository.delete(token) }
    }

    @Test
    fun `resendEmailConfirmation should generate token and publish event`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = false
        )
        val token = Token(
            secret = "secret123",
            creator = user,
            type = TokenType.EmailConfirmation,
            createdOn = Instant.now()
        )

        every { tokenRepository.save(any<Token<TokenType.EmailConfirmation>>()) } returns token
        every { tokenRepository.findByCreatorAndType(user, token.type) } returns token

        mockRequestContext()

        emailConfirmationService.resendEmailConfirmation(user)

        verify(exactly = 1) { tokenRepository.save(any<Token<TokenType.EmailConfirmation>>()) }
        verify(exactly = 1) { eventPublisher.publishEvent(ofType<EmailNeedsConfirmationEvent>()) }
    }

    @Test
    fun `resendEmailConfirmation should not generate token when email already confirmed`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = true
        )

        emailConfirmationService.resendEmailConfirmation(user)

        verify(exactly = 0) { tokenRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `resendEmailConfirmation should handle user with unconfirmed email`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = false
        )
        val token = Token(
            secret = "newSecret",
            creator = user,
            type = TokenType.EmailConfirmation,
            createdOn = Instant.now()
        )

        every { tokenRepository.save(any<Token<TokenType.EmailConfirmation>>()) } returns token
        every { tokenRepository.findByCreatorAndType(user, token.type) } returns token

        mockRequestContext()

        emailConfirmationService.resendEmailConfirmation(user)

        verify(exactly = 1) { eventPublisher.publishEvent(ofType<EmailNeedsConfirmationEvent>()) }
    }

    @Test
    fun `confirmEmail should not save user when token is invalid`() {
        every { tokenRepository.findBySecret("invalid") } returns null

        emailConfirmationService.confirmEmail("invalid")

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `confirmEmail should set emailConfirmed to true`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = false
        )
        val token = Token(
            secret = "newSecret",
            creator = user,
            type = TokenType.EmailConfirmation,
            createdOn = Instant.now()
        )

        every { tokenRepository.findBySecret("validSecret") } returns token
        every { userRepository.save(user) } returns user
        every { tokenRepository.delete(token) } just Runs

        assertFalse(user.emailConfirmed)

        emailConfirmationService.confirmEmail("validSecret")

        assertTrue(user.emailConfirmed)
    }
}

