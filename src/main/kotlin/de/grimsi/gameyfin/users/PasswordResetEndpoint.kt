package de.grimsi.gameyfin.users

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Roles
import de.grimsi.gameyfin.shared.token.TokenValidationResult
import jakarta.annotation.security.RolesAllowed

@Endpoint
@AnonymousAllowed
class PasswordResetEndpoint(
    private val passwordResetService: PasswordResetService,
    private val userService: UserService
) {

    fun requestPasswordReset(email: String) {
        passwordResetService.requestPasswordReset(email)

        // No return value to prevent enumeration attacks
    }

    @RolesAllowed(Roles.Names.ADMIN)
    fun createPasswordResetTokenForUser(username: String): String {
        return passwordResetService.generate(username).secret
    }

    fun resetPassword(secret: String, newPassword: String): TokenValidationResult {
        return passwordResetService.resetPassword(secret, newPassword)
    }
}