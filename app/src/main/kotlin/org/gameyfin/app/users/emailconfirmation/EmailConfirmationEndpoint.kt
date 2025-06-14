package org.gameyfin.app.users.emailconfirmation

import com.vaadin.hilla.Endpoint
import org.gameyfin.app.users.UserService
import jakarta.annotation.security.PermitAll
import org.gameyfin.app.shared.token.TokenValidationResult
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

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
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        userService.getByUsername(auth.name)?.let {
            emailConfirmationService.resendEmailConfirmation(it)
        }
    }
}