package de.grimsi.gameyfin.users.registration

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Roles
import de.grimsi.gameyfin.shared.token.TokenDto
import de.grimsi.gameyfin.shared.token.TokenValidationResult
import de.grimsi.gameyfin.users.UserService
import de.grimsi.gameyfin.users.dto.UserRegistrationDto
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

    fun acceptInvitation(token: String, registration: UserRegistrationDto): TokenValidationResult {
        return invitationService.acceptInvitation(token, registration)
    }

    fun getInvitationRecipientEmail(token: String): String? {
        return invitationService.getAssociatedEmail(token)
    }

    @RolesAllowed(Roles.Names.ADMIN)
    fun confirmRegistration(username: String) {
        userService.confirmRegistration(username)
    }

    @RolesAllowed(Roles.Names.ADMIN)
    fun createInvitation(email: String): TokenDto {
        return invitationService.createInvitation(email)
    }
}