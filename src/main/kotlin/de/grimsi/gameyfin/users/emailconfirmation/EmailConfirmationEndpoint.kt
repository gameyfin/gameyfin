package de.grimsi.gameyfin.users.emailconfirmation

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.shared.token.TokenValidationResult
import de.grimsi.gameyfin.users.UserService
import jakarta.annotation.security.PermitAll
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