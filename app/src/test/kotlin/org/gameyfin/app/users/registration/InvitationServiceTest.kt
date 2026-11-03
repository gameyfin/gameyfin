package org.gameyfin.app.users.registration

import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import org.gameyfin.app.core.events.AccountStatusChangedEvent
import org.gameyfin.app.core.events.UserInvitationEvent
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.core.token.Token
import org.gameyfin.app.core.token.TokenRepository
import org.gameyfin.app.core.token.TokenType
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.dto.UserRegistrationDto
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.enums.UserInvitationAcceptanceResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InvitationServiceTest {

    private lateinit var tokenRepository: TokenRepository
    private lateinit var userService: UserService
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var invitationService: InvitationService

    @BeforeEach
    fun setup() {
        tokenRepository = mockk()
        userService = mockk()
        eventPublisher = mockk()

        invitationService = InvitationService(tokenRepository, userService, eventPublisher)

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
    fun `createInvitation should throw when email already registered`() {
        every { userService.existsByEmail("taken@example.com") } returns true

        assertThrows(IllegalStateException::class.java) {
            invitationService.createInvitation("taken@example.com")
        }
    }

    @Test
    fun `createInvitation should throw when no authentication found`() {
        every { userService.existsByEmail("new@example.com") } returns false
        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns null

        assertThrows(IllegalStateException::class.java) {
            invitationService.createInvitation("new@example.com")
        }
    }

    @Test
    fun `createInvitation should throw when auth user not found`() {
        every { userService.existsByEmail("invitee@example.com") } returns false
        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        val auth = mockk<org.springframework.security.core.Authentication> { every { name } returns "missingUser" }
        every { getCurrentAuth() } returns auth
        every { userService.getByUsername("missingUser") } returns null

        assertThrows(IllegalStateException::class.java) {
            invitationService.createInvitation("invitee@example.com")
        }
    }

    @Test
    fun `createInvitation should generate token and publish event`() {
        every { userService.existsByEmail("invitee@example.com") } returns false
        val inviter = User(id = 1L, username = "inviter", email = "inviter@example.com", enabled = true)
        val auth = mockk<org.springframework.security.core.Authentication> { every { name } returns "inviter" }
        val token =
            Token(type = TokenType.Invitation, creator = inviter, payload = mapOf("email" to "invitee@example.com"))

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userService.getByUsername("inviter") } returns inviter
        every { tokenRepository.findByCreatorAndType(inviter, TokenType.Invitation) } returns null
        every { tokenRepository.findByCreatorAndTypeAndPayload(inviter, TokenType.Invitation, any()) } returns null
        every { tokenRepository.save(any<Token<TokenType.Invitation>>()) } returns token

        mockRequestContext()

        val result = invitationService.createInvitation("invitee@example.com")

        assertEquals(token.secret, result.secret)
        verify { eventPublisher.publishEvent(ofType<UserInvitationEvent>()) }
    }

    @Test
    fun `getAssociatedEmail should return email from payload`() {
        val inviter = User(id = 1L, username = "inviter", email = "inviter@example.com")
        val token =
            Token(type = TokenType.Invitation, creator = inviter, payload = mapOf("email" to "invitee@example.com"))

        every { tokenRepository.findBySecret(token.secret) } returns token

        val result = invitationService.getAssociatedEmail(token.secret)
        assertEquals("invitee@example.com", result)
    }

    @Test
    fun `getAssociatedEmail should return null when token missing`() {
        every { tokenRepository.findBySecret("missing") } returns null

        val result = invitationService.getAssociatedEmail("missing")
        assertNull(result)
    }

    @Test
    fun `getAssociatedEmail should return null when payload has no email`() {
        val inviter = User(id = 1L, username = "inviter", email = "inviter@example.com")
        val token = Token(type = TokenType.Invitation, creator = inviter, payload = emptyMap())
        every { tokenRepository.findBySecret(token.secret) } returns token

        val result = invitationService.getAssociatedEmail(token.secret)
        assertNull(result)
    }

    @Test
    fun `acceptInvitation should return TOKEN_INVALID when token not found`() {
        every { tokenRepository.findBySecret("invalid") } returns null

        val result = invitationService.acceptInvitation("invalid", UserRegistrationDto("u", "p", "e"))
        assertEquals(UserInvitationAcceptanceResult.TOKEN_INVALID, result)
    }

    @Test
    fun `acceptInvitation should return TOKEN_INVALID when token has no email`() {
        val inviter = User(id = 1L, username = "inviter", email = "inviter@example.com")
        val token = Token(type = TokenType.Invitation, creator = inviter, payload = emptyMap())
        every { tokenRepository.findBySecret(token.secret) } returns token

        val result = invitationService.acceptInvitation(token.secret, UserRegistrationDto("u", "p", "e"))
        assertEquals(UserInvitationAcceptanceResult.TOKEN_INVALID, result)
    }

    @Test
    fun `acceptInvitation should return USERNAME_TAKEN when registration throws`() {
        val inviter = User(id = 1L, username = "inviter", email = "inviter@example.com")
        val token =
            Token(type = TokenType.Invitation, creator = inviter, payload = mapOf("email" to "invitee@example.com"))
        every { tokenRepository.findBySecret(token.secret) } returns token
        every { userService.registerUserFromInvitation(any(), any()) } throws IllegalStateException("exists")

        val result = invitationService.acceptInvitation(token.secret, UserRegistrationDto("u", "p", "e"))
        assertEquals(UserInvitationAcceptanceResult.USERNAME_TAKEN, result)
        verify(exactly = 0) { tokenRepository.delete(any()) }
    }

    @Test
    fun `acceptInvitation should register user, delete token and publish event`() {
        val inviter = User(id = 1L, username = "inviter", email = "inviter@example.com")
        val newUser = User(id = 2L, username = "new", email = "invitee@example.com")
        val token =
            Token(type = TokenType.Invitation, creator = inviter, payload = mapOf("email" to "invitee@example.com"))
        every { tokenRepository.findBySecret(token.secret) } returns token
        every { userService.registerUserFromInvitation(any(), any()) } returns newUser
        every { tokenRepository.delete(token) } just Runs

        mockRequestContext()

        val result =
            invitationService.acceptInvitation(token.secret, UserRegistrationDto("new", "pass", "invitee@example.com"))
        assertEquals(UserInvitationAcceptanceResult.SUCCESS, result)
        verify { tokenRepository.delete(token) }
        verify { eventPublisher.publishEvent(ofType<AccountStatusChangedEvent>()) }
    }

    @Test
    fun `createInvitation should delete existing identical payload token`() {
        every { userService.existsByEmail("invitee@example.com") } returns false
        val inviter = User(id = 1L, username = "inviter", email = "inviter@example.com", enabled = true)
        val auth = mockk<org.springframework.security.core.Authentication> { every { name } returns "inviter" }
        val existingToken =
            Token(type = TokenType.Invitation, creator = inviter, payload = mapOf("email" to "invitee@example.com"))
        val newToken =
            Token(type = TokenType.Invitation, creator = inviter, payload = mapOf("email" to "invitee@example.com"))
        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userService.getByUsername("inviter") } returns inviter
        every { tokenRepository.findByCreatorAndType(inviter, TokenType.Invitation) } returns null
        every {
            tokenRepository.findByCreatorAndTypeAndPayload(
                inviter,
                TokenType.Invitation,
                existingToken.payload
            )
        } returns existingToken
        every { tokenRepository.delete(existingToken) } just Runs
        every { tokenRepository.save(any<Token<TokenType.Invitation>>()) } returns newToken

        mockRequestContext()

        invitationService.createInvitation("invitee@example.com")

        verify { tokenRepository.delete(existingToken) }
    }
}

