package org.gameyfin.app.core.token

import java.time.Instant
import kotlin.time.toJavaDuration

data class TokenDto(
    val secret: String,
    val type: String,
    val expiresAt: String
) {
    constructor(token: Token<*>) : this(
        secret = token.secret,
        type = token.type.key,
        expiresAt = if (token.type.expiration.isFinite()) {
            token.createdOn?.plus(token.type.expiration.toJavaDuration())?.toString() ?: Instant.MIN.toString()
        } else {
            "never"
        }
    )
}