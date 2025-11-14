package org.gameyfin.app.users.passwordreset

import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import org.gameyfin.app.core.events.PasswordResetRequestEvent
import org.gameyfin.app.core.token.Token
import org.gameyfin.app.core.token.TokenRepository
import org.gameyfin.app.core.token.TokenType
import org.gameyfin.app.core.token.TokenValidationResult
import org.gameyfin.app.messages.MessageService
import org.gameyfin.app.users.SessionService
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.entities.User
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Instant
import kotlin.test.assertEquals

class PasswordResetServiceTest {

    private lateinit var tokenRepository: TokenRepository
    private lateinit var userService: UserService
    private lateinit var messageService: MessageService
    private lateinit var sessionService: SessionService
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var passwordResetService: PasswordResetService

    @BeforeEach
    fun setup() {
        tokenRepository = mockk()
        userService = mockk()
        messageService = mockk()
        sessionService = mockk()
        eventPublisher = mockk()

        passwordResetService = PasswordResetService(
            tokenRepository,
            userService,
            messageService,
            sessionService,
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
    fun `generate should throw exception for OIDC user`() {
        val user = User(
            id = 1L,
            username = "oidcuser",
            email = "oidc@example.com",
            oidcProviderId = "oidc123"
        )

        assertThrows(IllegalStateException::class.java) {
            passwordResetService.generate(user)
        }
    }

    @Test
    fun `generate should create token for standard user`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            password = "password"
        )
        val token = Token(
            secret = "secret123",
            creator = user,
            type = TokenType.PasswordReset,
            createdOn = Instant.now()
        )

        every { tokenRepository.save(any<Token<TokenType.PasswordReset>>()) } returns token
        every { tokenRepository.findByCreatorAndType(user, token.type) } returns token

        val result = passwordResetService.generate(user)

        assertEquals(user, result.creator)
        verify(exactly = 1) { tokenRepository.save(any<Token<TokenType.PasswordReset>>()) }
    }

    @Test
    fun `generate with username should create token when conditions are met`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = false,
            password = "password"
        )
        val token = Token(
            secret = "secret123",
            creator = user,
            type = TokenType.PasswordReset,
            createdOn = Instant.now()
        )

        every { userService.getByUsername("testuser") } returns user
        every { messageService.enabled } returns false
        every { tokenRepository.save(any<Token<TokenType.PasswordReset>>()) } returns token
        every { tokenRepository.findByCreatorAndType(user, token.type) } returns token

        val result = passwordResetService.generate("testuser")

        assertEquals("secret123", result.secret)
        verify(exactly = 1) { tokenRepository.save(any<Token<TokenType.PasswordReset>>()) }
    }

    @Test
    fun `generate with username should throw exception when user not found`() {
        every { userService.getByUsername("nonexistent") } returns null

        assertThrows(IllegalArgumentException::class.java) {
            passwordResetService.generate("nonexistent")
        }
    }

    @Test
    fun `generate with username should throw exception when self-service enabled and email confirmed`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = true,
            password = "password"
        )

        every { userService.getByUsername("testuser") } returns user
        every { messageService.enabled } returns true

        assertThrows(IllegalStateException::class.java) {
            passwordResetService.generate("testuser")
        }
    }

    @Test
    fun `generate with username should throw exception for OIDC user`() {
        val user = User(
            id = 1L,
            username = "oidcuser",
            email = "oidc@example.com",
            oidcProviderId = "oidc123"
        )

        every { userService.getByUsername("oidcuser") } returns user
        every { messageService.enabled } returns false

        assertThrows(IllegalStateException::class.java) {
            passwordResetService.generate("oidcuser")
        }
    }

    @Test
    fun `requestPasswordReset should not process when user not found`() {
        every { userService.getByEmail("nonexistent@example.com") } returns null

        passwordResetService.requestPasswordReset("nonexistent@example.com")

        verify(exactly = 0) { tokenRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `requestPasswordReset should not process when email not confirmed`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = false,
            password = "password"
        )

        every { userService.getByEmail("test@example.com") } returns user

        passwordResetService.requestPasswordReset("test@example.com")

        verify(exactly = 0) { tokenRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `requestPasswordReset should not process for OIDC user`() {
        val user = User(
            id = 1L,
            username = "oidcuser",
            email = "oidc@example.com",
            emailConfirmed = true,
            oidcProviderId = "oidc123"
        )

        every { userService.getByEmail("oidc@example.com") } returns user

        passwordResetService.requestPasswordReset("oidc@example.com")

        verify(exactly = 0) { tokenRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `requestPasswordReset should create token and publish event for valid request`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = true,
            password = "password"
        )
        val token = Token(
            secret = "secret123",
            creator = user,
            type = TokenType.PasswordReset,
            createdOn = Instant.now()
        )

        every { userService.getByEmail("test@example.com") } returns user
        every { tokenRepository.save(any<Token<TokenType.PasswordReset>>()) } returns token
        every { tokenRepository.findByCreatorAndType(user, token.type) } returns token

        mockRequestContext()

        passwordResetService.requestPasswordReset("test@example.com")

        verify(exactly = 1) { tokenRepository.save(any<Token<TokenType.PasswordReset>>()) }
        verify(exactly = 1) { eventPublisher.publishEvent(ofType<PasswordResetRequestEvent>()) }
    }

    @Test
    fun `resetPassword should return INVALID when token not found`() {
        every { tokenRepository.findBySecret("invalid") } returns null

        val result = passwordResetService.resetPassword("invalid", "newPassword")

        assertEquals(TokenValidationResult.INVALID, result)
        verify(exactly = 0) { userService.updatePassword(any(), any()) }
    }

    @Test
    fun `resetPassword should return EXPIRED when token is expired`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            password = "oldPassword"
        )
        val token = Token(
            secret = "secret123",
            creator = user,
            type = TokenType.PasswordReset,
            createdOn = Instant.MIN
        )

        every { tokenRepository.findBySecret("expiredSecret") } returns token
        every { userService.updatePassword(user, "newPassword") } returns Unit
        every { sessionService.logoutAllSessions(user) } returns Unit

        val result = passwordResetService.resetPassword("expiredSecret", "newPassword")

        assertEquals(TokenValidationResult.EXPIRED, result)
        verify(exactly = 0) { userService.updatePassword(any(), any()) }
    }

    @Test
    fun `resetPassword should update password and logout sessions for valid token`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            password = "oldPassword"
        )
        val token = Token(
            secret = "secret123",
            creator = user,
            type = TokenType.PasswordReset,
            createdOn = Instant.now()
        )

        every { tokenRepository.findBySecret("validSecret") } returns token
        every { userService.updatePassword(user, "newPassword") } just Runs
        every { tokenRepository.delete(token) } just Runs
        every { sessionService.logoutAllSessions(user) } just Runs

        val result = passwordResetService.resetPassword("validSecret", "newPassword")

        assertEquals(TokenValidationResult.VALID, result)
        verify(exactly = 1) { userService.updatePassword(user, "newPassword") }
        verify(exactly = 1) { tokenRepository.delete(token) }
        verify(exactly = 1) { sessionService.logoutAllSessions(user) }
    }

    @Test
    fun `resetPassword should delete token after successful reset`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            password = "oldPassword"
        )
        val token = Token(
            secret = "secret123",
            creator = user,
            type = TokenType.PasswordReset,
            createdOn = Instant.now()
        )

        every { tokenRepository.findBySecret("validSecret") } returns token
        every { userService.updatePassword(user, "newPassword") } just Runs
        every { tokenRepository.delete(token) } just Runs
        every { sessionService.logoutAllSessions(user) } just Runs

        passwordResetService.resetPassword("validSecret", "newPassword")

        verify(exactly = 1) { tokenRepository.delete(token) }
    }

    @Test
    fun `resetPassword should handle empty new password`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            password = "oldPassword"
        )

        val token = Token(
            secret = "secret123",
            creator = user,
            type = TokenType.PasswordReset,
            createdOn = Instant.now()
        )

        every { tokenRepository.findBySecret("validSecret") } returns token
        every { userService.updatePassword(user, "") } just Runs
        every { tokenRepository.delete(token) } just Runs
        every { sessionService.logoutAllSessions(user) } just Runs

        val result = passwordResetService.resetPassword("validSecret", "")

        assertEquals(TokenValidationResult.VALID, result)
        verify(exactly = 1) { userService.updatePassword(user, "") }
    }

    @Test
    fun `generate with username should handle user with unconfirmed email when messages disabled`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = false,
            password = "password"
        )
        val token = Token(
            secret = "secret123",
            creator = user,
            type = TokenType.PasswordReset,
            createdOn = Instant.now()
        )

        every { userService.getByUsername("testuser") } returns user
        every { messageService.enabled } returns false
        every { tokenRepository.save(any<Token<TokenType.PasswordReset>>()) } returns token
        every { tokenRepository.findByCreatorAndType(user, token.type) } returns token

        val result = passwordResetService.generate("testuser")

        assertEquals("secret123", result.secret)
    }

    @Test
    fun `requestPasswordReset should handle timing attack prevention delay`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = true,
            password = "password"
        )
        val token = Token(
            secret = "secret123",
            creator = user,
            type = TokenType.PasswordReset,
            createdOn = Instant.now()
        )

        every { userService.getByEmail("test@example.com") } returns user
        every { tokenRepository.save(any<Token<TokenType.PasswordReset>>()) } returns token
        every { tokenRepository.findByCreatorAndType(user, token.type) } returns token

        mockRequestContext()

        passwordResetService.requestPasswordReset("test@example.com")

        verify(exactly = 1) { eventPublisher.publishEvent(ofType<PasswordResetRequestEvent>()) }
    }
}

