package org.gameyfin.app.users.passwordreset

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import org.gameyfin.app.shared.token.TokenDto
import org.gameyfin.app.users.UserService
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.shared.token.TokenValidationResult

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

    @RolesAllowed(Role.Names.ADMIN)
    fun createPasswordResetTokenForUser(username: String): TokenDto {
        return passwordResetService.generate(username)
    }

    fun resetPassword(secret: String, newPassword: String): TokenValidationResult {
        return passwordResetService.resetPassword(secret, newPassword)
    }
}