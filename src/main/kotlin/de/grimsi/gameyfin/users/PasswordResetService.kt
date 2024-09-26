package de.grimsi.gameyfin.users

import de.grimsi.gameyfin.core.Utils
import de.grimsi.gameyfin.core.events.PasswordResetRequestEvent
import de.grimsi.gameyfin.messages.MessageService
import de.grimsi.gameyfin.shared.token.Token
import de.grimsi.gameyfin.shared.token.TokenRepository
import de.grimsi.gameyfin.shared.token.TokenService
import de.grimsi.gameyfin.shared.token.TokenType.PasswordReset
import de.grimsi.gameyfin.shared.token.TokenValidationResult
import de.grimsi.gameyfin.users.entities.User
import io.github.oshai.kotlinlogging.KotlinLogging
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
) : TokenService<PasswordReset>(PasswordReset, tokenRepository) {

    private val log = KotlinLogging.logger {}

    private val secureRandom = SecureRandom()

    private val baseUrl: String
        get() = Utils.getBaseUrl()

    override fun generate(user: User): Token<PasswordReset> {
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
    fun generate(username: String): Token<PasswordReset> {
        if (messageService.enabled) {
            throw IllegalStateException("Cannot create password reset token for user '$username' because self-service is enabled")
        }

        val user = userService.getByUsername(username)
            ?: throw IllegalArgumentException("Cannot create password reset token for user '$username' because user does not exist")

        if (user.emailConfirmed == true) {
            throw IllegalStateException("Cannot create password reset token for user '$username' because self-service is enabled")
        }

        return generate(user)
    }

    /**
     * Users can request a password reset when the following conditions are met:
     * - The user has confirmed their email address
     * - The user is not managed externally
     */
    fun requestPasswordReset(email: String) {

        val maskedEmail = Utils.maskEmail(email)

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
        eventPublisher.publishEvent(PasswordResetRequestEvent(this, token, baseUrl))

        // Simulate a delay to prevent timing attacks
        Thread.sleep(secureRandom.nextLong(1024))
    }

    fun resetPassword(token: String, newPassword: String): TokenValidationResult {
        val passwordResetToken = get(token, PasswordReset)
            ?: return TokenValidationResult.INVALID

        if (passwordResetToken.expired) {
            return TokenValidationResult.EXPIRED
        }

        val user = passwordResetToken.user

        userService.updatePassword(user, newPassword)
        delete(passwordResetToken)
        sessionService.logoutAllSessions(user)
        return TokenValidationResult.VALID
    }
}