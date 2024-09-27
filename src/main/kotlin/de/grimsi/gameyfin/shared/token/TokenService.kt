package de.grimsi.gameyfin.shared.token

import de.grimsi.gameyfin.users.entities.User
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional

abstract class TokenService<T : TokenType>(
    private val type: T,
    private val tokenRepository: TokenRepository
) {

    private val log = KotlinLogging.logger {}

    @Transactional
    open fun generate(user: User): Token<T> {
        val token = Token(
            creator = user,
            type = type
        )

        tokenRepository.findByCreatorAndType(user, type)?.let {
            log.warn { "Deleting existing ${it.type.key} token for user '${user.username}'" }
            delete(it)
        }

        return tokenRepository.save(token)
    }

    @Transactional
    open fun generateWithPayload(user: User, payload: Map<String, String>): Token<T> {
        val token = Token(
            creator = user,
            type = type,
            payload = payload
        )

        tokenRepository.findByCreatorAndTypeAndPayload(user, type, payload)?.let {
            log.warn { "Deleting existing ${it.type.key} token of user '${user.username}' with same payload" }
            delete(it)
        }

        return tokenRepository.save(token)
    }

    @Transactional
    open fun get(secret: String, type: T): Token<T>? {
        val token = tokenRepository.findBySecret(secret) ?: return null

        return if (token.type == type) {
            @Suppress("UNCHECKED_CAST")
            token as Token<T>
        } else {
            log.error { "Token '$token' is not of type '$type'" }
            null
        }
    }

    @Transactional
    open fun getPayload(secret: String): Map<String, String>? {
        return tokenRepository.findBySecret(secret)?.payload
    }

    @Transactional
    open fun delete(token: Token<T>) {
        try {
            tokenRepository.delete(token)
        } catch (_: Exception) {
            log.warn { "Token '$token' has already been deleted" }
        }
    }

    @Transactional
    open fun validate(secret: String): TokenValidationResult {
        val token = tokenRepository.findBySecret(secret) ?: return TokenValidationResult.INVALID
        return if (token.expired) TokenValidationResult.EXPIRED else TokenValidationResult.VALID
    }
}