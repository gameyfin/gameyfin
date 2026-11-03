package org.gameyfin.app.users.registration

import io.mockk.*
import org.gameyfin.app.core.token.TokenDto
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.dto.UserRegistrationDto
import org.gameyfin.app.users.enums.UserInvitationAcceptanceResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistrationEndpointTest {

    private lateinit var userService: UserService
    private lateinit var invitationService: InvitationService
    private lateinit var registrationEndpoint: RegistrationEndpoint

    @BeforeEach
    fun setup() {
        userService = mockk()
        invitationService = mockk()
        registrationEndpoint = RegistrationEndpoint(userService, invitationService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `isSelfRegistrationAllowed should return true`() {
        every { userService.selfRegistrationAllowed } returns true

        assertTrue(registrationEndpoint.isSelfRegistrationAllowed())
    }

    @Test
    fun `isSelfRegistrationAllowed should return false`() {
        every { userService.selfRegistrationAllowed } returns false

        assertFalse(registrationEndpoint.isSelfRegistrationAllowed())
    }

    @Test
    fun `registerUser should delegate to service`() {
        val dto = UserRegistrationDto("user", "pass", "email@example.com")
        every { userService.selfRegisterUser(dto) } just Runs

        registrationEndpoint.registerUser(dto)

        verify { userService.selfRegisterUser(dto) }
    }

    @Test
    fun `isUsernameAvailable should return true when username free`() {
        every { userService.existsByUsername("free") } returns false
        assertTrue(registrationEndpoint.isUsernameAvailable("free"))
    }

    @Test
    fun `isUsernameAvailable should return false when username exists`() {
        every { userService.existsByUsername("taken") } returns true
        assertFalse(registrationEndpoint.isUsernameAvailable("taken"))
    }

    @Test
    fun `acceptInvitation should return SUCCESS`() {
        val dto = UserRegistrationDto("u", "p", "e")
        every { invitationService.acceptInvitation("token", dto) } returns UserInvitationAcceptanceResult.SUCCESS
        val result = registrationEndpoint.acceptInvitation("token", dto)
        assertEquals(UserInvitationAcceptanceResult.SUCCESS, result)
    }

    @Test
    fun `acceptInvitation should return TOKEN_INVALID`() {
        val dto = UserRegistrationDto("u", "p", "e")
        every { invitationService.acceptInvitation("bad", dto) } returns UserInvitationAcceptanceResult.TOKEN_INVALID
        val result = registrationEndpoint.acceptInvitation("bad", dto)
        assertEquals(UserInvitationAcceptanceResult.TOKEN_INVALID, result)
    }

    @Test
    fun `getInvitationRecipientEmail should return email`() {
        every { invitationService.getAssociatedEmail("tok") } returns "invitee@example.com"
        assertEquals("invitee@example.com", registrationEndpoint.getInvitationRecipientEmail("tok"))
    }

    @Test
    fun `getInvitationRecipientEmail should return null`() {
        every { invitationService.getAssociatedEmail("tok") } returns null
        assertEquals(null, registrationEndpoint.getInvitationRecipientEmail("tok"))
    }

    @Test
    fun `createInvitation should return token dto`() {
        val tokenDto = TokenDto("secret", "invitation", "2025-12-31T00:00:00Z")
        every { invitationService.createInvitation("new@example.com") } returns tokenDto
        val result = registrationEndpoint.createInvitation("new@example.com")
        assertEquals(tokenDto, result)
    }

    @Test
    fun `createInvitation should delegate to service`() {
        val tokenDto = TokenDto("secret2", "invitation", "2025-12-31T00:00:00Z")
        every { invitationService.createInvitation("new2@example.com") } returns tokenDto
        registrationEndpoint.createInvitation("new2@example.com")
        verify { invitationService.createInvitation("new2@example.com") }
    }
}

