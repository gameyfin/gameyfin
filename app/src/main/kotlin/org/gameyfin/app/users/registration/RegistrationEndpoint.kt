package org.gameyfin.app.users.registration

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import org.gameyfin.app.shared.token.TokenDto
import org.gameyfin.app.users.UserService
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.users.dto.UserRegistrationDto
import org.gameyfin.app.users.enums.UserInvitationAcceptanceResult

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