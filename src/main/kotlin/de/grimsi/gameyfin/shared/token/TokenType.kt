package de.grimsi.gameyfin.shared.token

import java.io.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

sealed class TokenType(
    val key: String,
    val expiration: Duration
) : Serializable {
    data object PasswordReset : TokenType("password-reset", 15.minutes)
    data object EmailVerification : TokenType("email-verification", Duration.INFINITE)
    data object Invitation : TokenType("invitation", Duration.INFINITE)

    fun readResolve(): Any = when (this) {
        PasswordReset -> PasswordReset
        EmailVerification -> EmailVerification
        Invitation -> Invitation
    }
}
