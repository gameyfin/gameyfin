package de.grimsi.gameyfin.users

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint

@Endpoint
@AnonymousAllowed
class PasswordResetEndpoint(
    private val passwordResetService: PasswordResetService
) {

    fun requestPasswordReset(email: String) {
        passwordResetService.requestPasswordReset(email)
    }

    fun resetPassword(token: String, newPassword: String) {

    }
}