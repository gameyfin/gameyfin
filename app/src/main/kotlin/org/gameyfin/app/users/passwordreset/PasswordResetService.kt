package org.gameyfin.app.users.passwordreset

import org.gameyfin.app.core.events.PasswordResetRequestEvent
import org.gameyfin.app.messages.MessageService
import org.gameyfin.app.users.SessionService
import org.gameyfin.app.users.UserService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.Utils
import org.gameyfin.app.shared.token.Token
import org.gameyfin.app.shared.token.TokenDto
import org.gameyfin.app.shared.token.TokenRepository
import org.gameyfin.app.shared.token.TokenService
import org.gameyfin.app.shared.token.TokenType
import org.gameyfin.app.shared.token.TokenValidationResult
import org.gameyfin.app.users.entities.User
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.security.SecureRandom

@Service
class PasswordResetService(
    tokenRepository: TokenRepository,
    private val userService: UserService,
    private val messageService: MessageService,
    private val sessionService: SessionService,
    private val eventPublisher: ApplicationEventPublisher
) : TokenService<TokenType.PasswordReset>(TokenType.PasswordReset, tokenRepository) {

    private val log = KotlinLogging.logger {}

    private val secureRandom = SecureRandom()

    override fun generate(user: User): Token<TokenType.PasswordReset> {
        if (user.oidcProviderId != null) {
            throw IllegalStateException("Cannot create password reset token for user '${user.username}' because user is managed externally")
        }

        return super.generate(user)
    }

    /**
     * Admins should be able to create password reset tokens for users when the following conditions are met:
     * - E-Mail notifications are not enabled
     * - The user has no confirmed email address
     * - The user is not managed externally
     */
    fun generate(username: String): TokenDto {

        val user = userService.getByUsername(username)
            ?: throw IllegalArgumentException("Cannot create password reset token for user '$username' because user does not exist")

        if (messageService.enabled && user.emailConfirmed) {
            throw IllegalStateException("Cannot create password reset token for user '$username' because self-service is enabled")
        }

        val token = generate(user)
        return TokenDto(token)
    }

    /**
     * Users can request a password reset when the following conditions are met:
     * - The user has confirmed their email address
     * - The user is not managed externally
     */
    fun requestPasswordReset(email: String) {

        val maskedEmail = Utils.Companion.maskEmail(email)

        log.info { "Initiating password reset request for '${maskedEmail}'" }

        val user = userService.getByEmail(email)

        if (user == null) {
            log.error { "No user with email '${maskedEmail}' found" }
            return
        }

        if (!user.emailConfirmed) {
            log.error { "User with email '${maskedEmail}' has not confirmed their email address" }
            return
        }

        if (user.oidcProviderId != null) {
            log.error { "User with email '${maskedEmail}' is managed externally" }
            return
        }

        val token = generate(user)
        eventPublisher.publishEvent(PasswordResetRequestEvent(this, token, Utils.Companion.getBaseUrl()))

        // Simulate a delay to prevent timing attacks
        Thread.sleep(secureRandom.nextLong(1024))
    }

    fun resetPassword(secret: String, newPassword: String): TokenValidationResult {
        val passwordResetToken = get(secret, TokenType.PasswordReset)
            ?: return TokenValidationResult.INVALID

        if (passwordResetToken.expired) {
            return TokenValidationResult.EXPIRED
        }

        val user = passwordResetToken.creator

        userService.updatePassword(user, newPassword)
        delete(passwordResetToken)
        sessionService.logoutAllSessions(user)
        return TokenValidationResult.VALID
    }
}