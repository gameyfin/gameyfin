package org.gameyfin.app.users.passwordreset

import io.mockk.*
import org.gameyfin.app.core.token.Token
import org.gameyfin.app.core.token.TokenDto
import org.gameyfin.app.core.token.TokenType
import org.gameyfin.app.core.token.TokenValidationResult
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.entities.User
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class PasswordResetEndpointTest {

    private lateinit var passwordResetService: PasswordResetService
    private lateinit var userService: UserService
    private lateinit var passwordResetEndpoint: PasswordResetEndpoint

    @BeforeEach
    fun setup() {
        passwordResetService = mockk()
        userService = mockk()
        passwordResetEndpoint = PasswordResetEndpoint(passwordResetService, userService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `requestPasswordReset should delegate to service`() {
        every { passwordResetService.requestPasswordReset("test@example.com") } just Runs

        passwordResetEndpoint.requestPasswordReset("test@example.com")

        verify(exactly = 1) { passwordResetService.requestPasswordReset("test@example.com") }
    }

    @Test
    fun `requestPasswordReset should handle valid email address`() {
        every { passwordResetService.requestPasswordReset("valid@example.com") } just Runs

        passwordResetEndpoint.requestPasswordReset("valid@example.com")

        verify(exactly = 1) { passwordResetService.requestPasswordReset("valid@example.com") }
    }

    @Test
    fun `requestPasswordReset should handle nonexistent email without revealing information`() {
        every { passwordResetService.requestPasswordReset("nonexistent@example.com") } just Runs

        passwordResetEndpoint.requestPasswordReset("nonexistent@example.com")

        verify(exactly = 1) { passwordResetService.requestPasswordReset("nonexistent@example.com") }
    }

    @Test
    fun `requestPasswordReset should handle empty email`() {
        every { passwordResetService.requestPasswordReset("") } just Runs

        passwordResetEndpoint.requestPasswordReset("")

        verify(exactly = 1) { passwordResetService.requestPasswordReset("") }
    }

    @Test
    fun `requestPasswordReset should handle email with special characters`() {
        val email = "test+tag@example.com"
        every { passwordResetService.requestPasswordReset(email) } just Runs

        passwordResetEndpoint.requestPasswordReset(email)

        verify(exactly = 1) { passwordResetService.requestPasswordReset(email) }
    }

    @Test
    fun `createPasswordResetTokenForUser should return token DTO`() {
        val token = Token(
            secret = "secret123",
            type = TokenType.PasswordReset,
            createdOn = Instant.now(),
            creator = mockk<User>()
        )
        val tokenDto = TokenDto(token)

        every { passwordResetService.generate("testuser") } returns tokenDto

        val result = passwordResetEndpoint.createPasswordResetTokenForUser("testuser")

        assertEquals(tokenDto, result)
        assertEquals("secret123", result.secret)
        verify(exactly = 1) { passwordResetService.generate("testuser") }
    }

    @Test
    fun `createPasswordResetTokenForUser should handle different usernames`() {
        val token = Token(
            secret = "secret456",
            type = TokenType.PasswordReset,
            createdOn = Instant.now(),
            creator = mockk<User>()
        )
        val tokenDto = TokenDto(token)

        every { passwordResetService.generate("anotheruser") } returns tokenDto

        val result = passwordResetEndpoint.createPasswordResetTokenForUser("anotheruser")

        assertEquals("secret456", result.secret)
    }

    @Test
    fun `createPasswordResetTokenForUser should delegate to service with correct username`() {
        val token = Token(
            secret = "secret789",
            type = TokenType.PasswordReset,
            createdOn = Instant.now(),
            creator = mockk<User>()
        )
        val tokenDto = TokenDto(token)

        every { passwordResetService.generate("specificuser") } returns tokenDto

        passwordResetEndpoint.createPasswordResetTokenForUser("specificuser")

        verify(exactly = 1) { passwordResetService.generate("specificuser") }
    }

    @Test
    fun `resetPassword should return VALID for valid token and password`() {
        every { passwordResetService.resetPassword("validSecret", "newPassword") } returns TokenValidationResult.VALID

        val result = passwordResetEndpoint.resetPassword("validSecret", "newPassword")

        assertEquals(TokenValidationResult.VALID, result)
        verify(exactly = 1) { passwordResetService.resetPassword("validSecret", "newPassword") }
    }

    @Test
    fun `resetPassword should return INVALID for invalid token`() {
        every {
            passwordResetService.resetPassword(
                "invalidSecret",
                "newPassword"
            )
        } returns TokenValidationResult.INVALID

        val result = passwordResetEndpoint.resetPassword("invalidSecret", "newPassword")

        assertEquals(TokenValidationResult.INVALID, result)
    }

    @Test
    fun `resetPassword should return EXPIRED for expired token`() {
        every {
            passwordResetService.resetPassword(
                "expiredSecret",
                "newPassword"
            )
        } returns TokenValidationResult.EXPIRED

        val result = passwordResetEndpoint.resetPassword("expiredSecret", "newPassword")

        assertEquals(TokenValidationResult.EXPIRED, result)
    }

    @Test
    fun `resetPassword should handle empty password`() {
        every { passwordResetService.resetPassword("validSecret", "") } returns TokenValidationResult.VALID

        val result = passwordResetEndpoint.resetPassword("validSecret", "")

        assertEquals(TokenValidationResult.VALID, result)
    }

    @Test
    fun `resetPassword should handle long password`() {
        val longPassword = "a".repeat(1000)
        every { passwordResetService.resetPassword("validSecret", longPassword) } returns TokenValidationResult.VALID

        val result = passwordResetEndpoint.resetPassword("validSecret", longPassword)

        assertEquals(TokenValidationResult.VALID, result)
    }

    @Test
    fun `resetPassword should handle password with special characters`() {
        val complexPassword = "P@ssw0rd!#\$%^&*()"
        every { passwordResetService.resetPassword("validSecret", complexPassword) } returns TokenValidationResult.VALID

        val result = passwordResetEndpoint.resetPassword("validSecret", complexPassword)

        assertEquals(TokenValidationResult.VALID, result)
    }

    @Test
    fun `resetPassword should delegate to service with correct parameters`() {
        val secret = "testSecret"
        val password = "testPassword"

        every { passwordResetService.resetPassword(secret, password) } returns TokenValidationResult.VALID

        passwordResetEndpoint.resetPassword(secret, password)

        verify(exactly = 1) { passwordResetService.resetPassword(secret, password) }
    }

    @Test
    fun `createPasswordResetTokenForUser should handle empty username`() {
        val user = mockk<User>()
        val token = Token(
            secret = "secret123",
            type = TokenType.PasswordReset,
            createdOn = Instant.now(),
            creator = user
        )
        val tokenDto = TokenDto(token)

        every { passwordResetService.generate("") } returns tokenDto
        every { user.username } returns ""

        val result = passwordResetEndpoint.createPasswordResetTokenForUser("")

        assertEquals("secret123", result.secret)
    }

    @Test
    fun `requestPasswordReset should handle uppercase email`() {
        every { passwordResetService.requestPasswordReset("TEST@EXAMPLE.COM") } just Runs

        passwordResetEndpoint.requestPasswordReset("TEST@EXAMPLE.COM")

        verify(exactly = 1) { passwordResetService.requestPasswordReset("TEST@EXAMPLE.COM") }
    }
}

