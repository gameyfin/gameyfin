package de.grimsi.gameyfin.users.emailconfirmation

import de.grimsi.gameyfin.core.Utils
import de.grimsi.gameyfin.core.events.EmailNeedsConfirmationEvent
import de.grimsi.gameyfin.shared.token.TokenRepository
import de.grimsi.gameyfin.shared.token.TokenService
import de.grimsi.gameyfin.shared.token.TokenType.EmailConfirmation
import de.grimsi.gameyfin.shared.token.TokenValidationResult
import de.grimsi.gameyfin.users.entities.User
import de.grimsi.gameyfin.users.persistence.UserRepository
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class EmailConfirmationService(
    tokenRepository: TokenRepository,
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher
) : TokenService<EmailConfirmation>(EmailConfirmation, tokenRepository) {

    val log: KLogger = KotlinLogging.logger {}

    fun confirmEmail(secret: String): TokenValidationResult {
        val emailConfirmationToken = get(secret, EmailConfirmation)
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
        eventPublisher.publishEvent(EmailNeedsConfirmationEvent(user, token, Utils.getBaseUrl()))
    }

    private fun confirmEmail(user: User) {
        user.emailConfirmed = true
        userRepository.save(user)
    }
}