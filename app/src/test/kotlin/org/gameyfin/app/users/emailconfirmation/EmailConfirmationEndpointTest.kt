package org.gameyfin.app.users.emailconfirmation

import io.mockk.*
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.core.token.TokenValidationResult
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.entities.User
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import kotlin.test.assertEquals

class EmailConfirmationEndpointTest {

    private lateinit var emailConfirmationService: EmailConfirmationService
    private lateinit var userService: UserService
    private lateinit var emailConfirmationEndpoint: EmailConfirmationEndpoint

    @BeforeEach
    fun setup() {
        emailConfirmationService = mockk()
        userService = mockk()
        emailConfirmationEndpoint = EmailConfirmationEndpoint(emailConfirmationService, userService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `confirmEmail should return VALID for valid token`() {
        every { emailConfirmationService.confirmEmail("validToken") } returns TokenValidationResult.VALID

        val result = emailConfirmationEndpoint.confirmEmail("validToken")

        assertEquals(TokenValidationResult.VALID, result)
        verify(exactly = 1) { emailConfirmationService.confirmEmail("validToken") }
    }

    @Test
    fun `confirmEmail should return INVALID for invalid token`() {
        every { emailConfirmationService.confirmEmail("invalidToken") } returns TokenValidationResult.INVALID

        val result = emailConfirmationEndpoint.confirmEmail("invalidToken")

        assertEquals(TokenValidationResult.INVALID, result)
    }

    @Test
    fun `confirmEmail should return EXPIRED for expired token`() {
        every { emailConfirmationService.confirmEmail("expiredToken") } returns TokenValidationResult.EXPIRED

        val result = emailConfirmationEndpoint.confirmEmail("expiredToken")

        assertEquals(TokenValidationResult.EXPIRED, result)
    }

    @Test
    fun `confirmEmail should delegate to service with correct token`() {
        val token = "testToken123"
        every { emailConfirmationService.confirmEmail(token) } returns TokenValidationResult.VALID

        emailConfirmationEndpoint.confirmEmail(token)

        verify(exactly = 1) { emailConfirmationService.confirmEmail(token) }
    }

    @Test
    fun `resendEmailConfirmation should resend for authenticated user`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = false
        )
        val auth = mockk<Authentication> {
            every { name } returns "testuser"
            every { principal } returns mockk<Any>()
            every { authorities } returns listOf(SimpleGrantedAuthority(Role.Names.ADMIN))
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userService.getByUsername("testuser") } returns user
        every { emailConfirmationService.resendEmailConfirmation(user) } just Runs

        emailConfirmationEndpoint.resendEmailConfirmation()

        verify(exactly = 1) { emailConfirmationService.resendEmailConfirmation(user) }
    }

    @Test
    fun `resendEmailConfirmation should throw exception when no authentication`() {
        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns null

        assertThrows(IllegalStateException::class.java) {
            emailConfirmationEndpoint.resendEmailConfirmation()
        }
    }

    @Test
    fun `resendEmailConfirmation should not call service when user not found`() {
        val auth = mockk<Authentication> {
            every { name } returns "testuser"
            every { principal } returns mockk<Any>()
            every { authorities } returns listOf(SimpleGrantedAuthority(Role.Names.ADMIN))
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userService.getByUsername("testuser") } returns null

        emailConfirmationEndpoint.resendEmailConfirmation()

        verify(exactly = 0) { emailConfirmationService.resendEmailConfirmation(any()) }
    }

    @Test
    fun `resendEmailConfirmation should handle user with unconfirmed email`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = false
        )
        val auth = mockk<Authentication> {
            every { name } returns "testuser"
            every { principal } returns mockk<Any>()
            every { authorities } returns listOf(SimpleGrantedAuthority(Role.Names.ADMIN))
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userService.getByUsername("testuser") } returns user
        every { emailConfirmationService.resendEmailConfirmation(user) } just Runs

        emailConfirmationEndpoint.resendEmailConfirmation()

        verify(exactly = 1) { emailConfirmationService.resendEmailConfirmation(user) }
    }

    @Test
    fun `resendEmailConfirmation should handle user with confirmed email`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = true
        )
        val auth = mockk<Authentication> {
            every { name } returns "testuser"
            every { principal } returns mockk<Any>()
            every { authorities } returns listOf(SimpleGrantedAuthority(Role.Names.ADMIN))
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userService.getByUsername("testuser") } returns user
        every { emailConfirmationService.resendEmailConfirmation(user) } just Runs

        emailConfirmationEndpoint.resendEmailConfirmation()

        verify(exactly = 1) { emailConfirmationService.resendEmailConfirmation(user) }
    }

    @Test
    fun `confirmEmail should handle empty token string`() {
        every { emailConfirmationService.confirmEmail("") } returns TokenValidationResult.INVALID

        val result = emailConfirmationEndpoint.confirmEmail("")

        assertEquals(TokenValidationResult.INVALID, result)
    }

    @Test
    fun `confirmEmail should handle long token string`() {
        val longToken = "a".repeat(1000)
        every { emailConfirmationService.confirmEmail(longToken) } returns TokenValidationResult.INVALID

        val result = emailConfirmationEndpoint.confirmEmail(longToken)

        assertEquals(TokenValidationResult.INVALID, result)
    }
}

