package de.grimsi.gameyfin.shared.token

import de.grimsi.gameyfin.users.entities.User
import io.github.oshai.kotlinlogging.KotlinLogging

abstract class TokenService<T : TokenType>(
    private val type: T,
    private val tokenRepository: TokenRepository
) {

    private val log = KotlinLogging.logger {}

    open fun generate(user: User): Token<T> {
        val token = Token(
            user = user,
            type = type
        )

        tokenRepository.findByUserAndType(user, type)?.let {
            log.warn { "Deleting existing '${it.type}' token for user '${user.username}'" }
            delete(it)
        }

        return tokenRepository.save(token)
    }

    fun get(secret: String, type: T): Token<T>? {
        val token = tokenRepository.findBySecret(secret) ?: return null

        return if (token.type == type) {
            @Suppress("UNCHECKED_CAST")
            token as Token<T>
        } else {
            log.error { "Token '$token' is not of type '$type'" }
            null
        }
    }

    fun delete(token: Token<T>) {
        tokenRepository.delete(token)
    }

    fun validate(secret: String): TokenValidationResult {
        val token = tokenRepository.findBySecret(secret) ?: return TokenValidationResult.INVALID
        return if (token.expired) TokenValidationResult.EXPIRED else TokenValidationResult.VALID
    }
}