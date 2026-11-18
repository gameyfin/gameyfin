package org.gameyfin.app.core.token

import java.time.Duration

sealed class TokenType(
    val key: String,
    val expiration: Duration?
) {
    data object PasswordReset : TokenType("password-reset", Duration.ofMinutes(15))
    data object EmailConfirmation : TokenType("email-verification", null)
    data object Invitation : TokenType("invitation", null)
}
