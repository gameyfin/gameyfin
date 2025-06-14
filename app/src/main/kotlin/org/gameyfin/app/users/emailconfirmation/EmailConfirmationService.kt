package org.gameyfin.app.users.emailconfirmation

import org.gameyfin.app.core.events.EmailNeedsConfirmationEvent
import org.gameyfin.app.shared.token.TokenRepository
import org.gameyfin.app.users.persistence.UserRepository
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.Utils
import org.gameyfin.app.shared.token.TokenService
import org.gameyfin.app.shared.token.TokenType
import org.gameyfin.app.shared.token.TokenValidationResult
import org.gameyfin.app.users.entities.User
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class EmailConfirmationService(
    tokenRepository: TokenRepository,
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher
) : TokenService<TokenType.EmailConfirmation>(TokenType.EmailConfirmation, tokenRepository) {

    val log: KLogger = KotlinLogging.logger {}

    fun confirmEmail(secret: String): TokenValidationResult {
        val emailConfirmationToken = get(secret, TokenType.EmailConfirmation)
            ?: return TokenValidationResult.INVALID

        if (emailConfirmationToken.expired) {
            return TokenValidationResult.EXPIRED
        }

        val user = emailConfirmationToken.creator
        confirmEmail(user)
        delete(emailConfirmationToken)

        return TokenValidationResult.VALID
    }

    fun resendEmailConfirmation(user: User) {
        if (user.emailConfirmed) {
            log.error { "User '${user.username}' has already confirmed their email address" }
            return
        }

        val token = generate(user)
        eventPublisher.publishEvent(EmailNeedsConfirmationEvent(user, token, Utils.Companion.getBaseUrl()))
    }

    private fun confirmEmail(user: User) {
        user.emailConfirmed = true
        userRepository.save(user)
    }
}