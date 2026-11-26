package org.gameyfin.app.users.passwordreset

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.Utils
import org.gameyfin.app.core.events.PasswordResetRequestEvent
import org.gameyfin.app.core.token.*
import org.gameyfin.app.messages.MessageService
import org.gameyfin.app.users.SessionService
import org.gameyfin.app.users.UserService
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
        check(user.oidcProviderId == null) { "Cannot create password reset token for user '${user.username}' because user is managed externally" }
        return super.generate(user)
    }

    /**
     * Admins can create password reset tokens for local accounts they manage when self-service is unavailable.
     */
    fun generate(username: String): TokenDto {

        val user = userService.getByUsername(username)
            ?: throw IllegalArgumentException("Cannot create password reset token for user '$username' because user does not exist")

        check(userService.canManage(user)) { "Cannot manage this user" }
        check(!(messageService.enabled && user.emailConfirmed && !user.email.isNullOrBlank())) { "Cannot create password reset token for user '$username' because self-service is enabled" }

        val token = generate(user)
        return TokenDto(token)
    }

    /**
     * Users can request a password reset when the following conditions are met:
     * - Messaging is enabled and the user has confirmed their email address
     * - The user is not managed externally
     */
    fun requestPasswordReset(email: String) {
        val address = email.trim()
        if (address.isEmpty()) return
        try {
            if (messageService.enabled) sendResetRequest(address)
        } finally {
            // Apply the same delay whether or not the address is eligible.
            Thread.sleep(secureRandom.nextLong(1024))
        }
    }

    private fun sendResetRequest(email: String) {
        val maskedEmail = Utils.maskEmail(email)

        log.info { "Initiating password reset request for '${maskedEmail}'" }

        val user = userService.getByEmail(email)

        if (user == null) {
            log.error { "No user with email '${maskedEmail}' found" }
            return
        }

        if (!user.emailConfirmed || user.email.isNullOrBlank()) {
            log.error { "User with email '${maskedEmail}' has not confirmed their email address" }
            return
        }

        if (user.oidcProviderId != null) {
            log.error { "User with email '${maskedEmail}' is managed externally" }
            return
        }

        val token = generate(user)
        eventPublisher.publishEvent(PasswordResetRequestEvent(this, token, Utils.getBaseUrl()))
    }

    fun resetPassword(secret: String, newPassword: String): TokenValidationResult {
        val passwordResetToken = get(secret, TokenType.PasswordReset)
            ?: return TokenValidationResult.INVALID

        if (passwordResetToken.expired) {
            return TokenValidationResult.EXPIRED
        }

        val user = passwordResetToken.creator
        if (user.oidcProviderId != null) return TokenValidationResult.INVALID

        userService.updatePassword(user, newPassword)
        delete(passwordResetToken)
        sessionService.logoutAllSessions(user)
        return TokenValidationResult.VALID
    }
}