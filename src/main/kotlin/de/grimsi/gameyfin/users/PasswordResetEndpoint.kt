package de.grimsi.gameyfin.users

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Roles
import de.grimsi.gameyfin.users.dto.PasswordResetResult
import jakarta.annotation.security.RolesAllowed

@Endpoint
@AnonymousAllowed
class PasswordResetEndpoint(
    private val passwordResetService: PasswordResetService
) {

    fun requestPasswordReset(email: String) {
        passwordResetService.requestPasswordReset(email)

        // No return value to prevent enumeration attacks
    }

    @RolesAllowed(Roles.Names.ADMIN)
    fun createPasswordResetTokenForUser(username: String): String {
        return passwordResetService.createPasswordResetToken(username)
    }

    fun resetPassword(token: String, newPassword: String): PasswordResetResult {
        return passwordResetService.resetPassword(token, newPassword)
    }
}