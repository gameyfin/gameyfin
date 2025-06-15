package org.gameyfin.app.shared.token

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

sealed class TokenType(
    val key: String,
    val expiration: Duration
) {
    data object PasswordReset : TokenType("password-reset", 15.minutes)
    data object EmailConfirmation : TokenType("email-verification", Duration.INFINITE)
    data object Invitation : TokenType("invitation", Duration.INFINITE)
}
