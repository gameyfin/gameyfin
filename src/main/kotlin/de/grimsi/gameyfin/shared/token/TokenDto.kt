package de.grimsi.gameyfin.shared.token

import java.time.Instant
import kotlin.time.toJavaDuration

data class TokenDto(
    val secret: String,
    val type: String,
    val expiresAt: Instant
) {
    constructor(token: Token<*>) : this(
        secret = token.secret,
        type = token.type.key,
        expiresAt = token.createdOn?.plus(token.type.expiration.toJavaDuration()) ?: Instant.MIN
    )
}