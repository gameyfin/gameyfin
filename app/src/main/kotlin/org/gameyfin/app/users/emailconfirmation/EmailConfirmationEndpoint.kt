package org.gameyfin.app.users.emailconfirmation

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.PermitAll
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.shared.token.TokenValidationResult
import org.gameyfin.app.users.UserService

@Endpoint
class EmailConfirmationEndpoint(
    private val emailConfirmationService: EmailConfirmationService,
    private val userService: UserService
) {

    @PermitAll
    fun confirmEmail(token: String): TokenValidationResult {
        return emailConfirmationService.confirmEmail(token)
    }

    @PermitAll
    fun resendEmailConfirmation() {
        val auth = getCurrentAuth()
        userService.getByUsername(auth.name)?.let {
            emailConfirmationService.resendEmailConfirmation(it)
        }
    }
}