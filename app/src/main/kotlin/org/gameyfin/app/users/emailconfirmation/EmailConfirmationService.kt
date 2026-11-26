package org.gameyfin.app.users.emailconfirmation

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.Utils
import org.gameyfin.app.core.events.EmailNeedsConfirmationEvent
import org.gameyfin.app.core.token.TokenRepository
import org.gameyfin.app.core.token.TokenService
import org.gameyfin.app.core.token.TokenType
import org.gameyfin.app.core.token.TokenValidationResult
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.persistence.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EmailConfirmationService(
    private val tokenRepository: TokenRepository,
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher
) : TokenService<TokenType.EmailConfirmation>(TokenType.EmailConfirmation, tokenRepository) {

    val log: KLogger = KotlinLogging.logger {}

    @Transactional
    fun confirmEmail(secret: String): TokenValidationResult {
        val userId = tokenRepository.findCreatorIdBySecret(secret) ?: return TokenValidationResult.INVALID
        // Lock the account before loading the token so an address change can remove stale tokens.
        val user = userRepository.findForUpdate(userId) ?: return TokenValidationResult.INVALID

        val emailConfirmationToken = get(secret, TokenType.EmailConfirmation)
            ?: return TokenValidationResult.INVALID

        if (emailConfirmationToken.expired) {
            return TokenValidationResult.EXPIRED
        }

        if (user.email.isNullOrBlank() || user.oidcProviderId != null) return TokenValidationResult.INVALID

        confirmEmail(user)
        delete(emailConfirmationToken)

        return TokenValidationResult.VALID
    }

    fun invalidate(user: User) {
        tokenRepository.findByCreatorAndType(user, TokenType.EmailConfirmation)?.let(tokenRepository::delete)
    }

    @Transactional
    fun resendEmailConfirmation(account: User) {
        val user = account.id?.let(userRepository::findForUpdate) ?: return
        if (user.email.isNullOrBlank() || user.oidcProviderId != null) return
        if (user.emailConfirmed) {
            log.error { "User '${user.username}' has already confirmed their email address" }
            return
        }

        val token = generate(user)
        eventPublisher.publishEvent(EmailNeedsConfirmationEvent(user, token, Utils.getBaseUrl()))
    }

    private fun confirmEmail(user: User) {
        user.emailConfirmed = true
        userRepository.save(user)
    }
}