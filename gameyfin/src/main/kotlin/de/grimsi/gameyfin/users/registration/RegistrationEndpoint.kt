package de.grimsi.gameyfin.users.registration

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.shared.token.TokenDto
import de.grimsi.gameyfin.users.UserService
import de.grimsi.gameyfin.users.dto.UserRegistrationDto
import de.grimsi.gameyfin.users.enums.UserInvitationAcceptanceResult
import jakarta.annotation.security.RolesAllowed

@AnonymousAllowed
@Endpoint
class RegistrationEndpoint(
    private val userService: UserService,
    private val invitationService: InvitationService
) {
    fun isSelfRegistrationAllowed(): Boolean {
        return userService.selfRegistrationAllowed
    }

    fun registerUser(registration: UserRegistrationDto) {
        userService.selfRegisterUser(registration)

        // No return value to prevent enumeration attacks
    }

    fun isUsernameAvailable(username: String): Boolean {
        return !userService.existsByUsername(username)
    }

    fun acceptInvitation(token: String, registration: UserRegistrationDto): UserInvitationAcceptanceResult {
        return invitationService.acceptInvitation(token, registration)
    }

    fun getInvitationRecipientEmail(token: String): String? {
        return invitationService.getAssociatedEmail(token)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun createInvitation(email: String): TokenDto {
        return invitationService.createInvitation(email)
    }
}