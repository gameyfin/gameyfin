package org.gameyfin.app.core.token

import java.time.Instant

data class TokenDto(
    val secret: String,
    val type: String,
    val expiresAt: String
) {
    constructor(token: Token<*>) : this(
        secret = token.secret,
        type = token.type.key,
        expiresAt = token.type.expiration?.let { expiration ->
            token.createdOn?.plus(expiration)?.toString() ?: Instant.MIN.toString()
        } ?: "never"
    )
}