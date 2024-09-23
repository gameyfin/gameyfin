package de.grimsi.gameyfin.users

import de.grimsi.gameyfin.core.Utils
import de.grimsi.gameyfin.core.events.PasswordResetRequestEvent
import de.grimsi.gameyfin.notifications.NotificationService
import de.grimsi.gameyfin.users.dto.PasswordResetResult
import de.grimsi.gameyfin.users.entities.PasswordResetToken
import de.grimsi.gameyfin.users.entities.User
import de.grimsi.gameyfin.users.persistence.PasswordResetTokenRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Instant
import java.util.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

@Service
class PasswordResetService(
    private val userService: UserService,
    private val sessionService: SessionService,
    private val notificationService: NotificationService,
    private val eventPublisher: ApplicationEventPublisher,
    private val passwordResetTokenRepository: PasswordResetTokenRepository
) {

    private companion object {
        val TOKEN_EXPIRATION = 24.hours
    }

    private val log = KotlinLogging.logger {}

    private val secureRandom = SecureRandom()

    private val PasswordResetToken.isExpired: Boolean
        get() = createdOn?.plus(TOKEN_EXPIRATION.toJavaDuration())!!.isBefore(Instant.now())

    private val baseUrl: String
        get() = Utils.getBaseUrl()

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

        val token = createPasswordResetToken(user)
        eventPublisher.publishEvent(PasswordResetRequestEvent(this, token, baseUrl))

        // Simulate a delay to prevent timing attacks
        Thread.sleep(secureRandom.nextLong(1024))
    }

    fun createPasswordResetToken(user: User): PasswordResetToken {
        if (user.oidcProviderId != null) {
            throw IllegalStateException("Cannot create password reset token for user '${user.username}' because user is managed externally")
        }

        val token = PasswordResetToken(
            user = user,
            token = UUID.randomUUID().toString()
        )

        passwordResetTokenRepository.findByUser(user)?.let {
            passwordResetTokenRepository.delete(it)
        }

        return passwordResetTokenRepository.save(token)
    }

    /**
     * Admins should be able to create password reset tokens for users when the following conditions are met:
     * - E-Mail notifications are not enabled
     * - The user has no confirmed email address
     * - The user is not managed externally
     */
    fun createPasswordResetToken(username: String): String {
        if (notificationService.enabled) {
            throw IllegalStateException("Cannot create password reset token for user '$username' because self-service is enabled")
        }

        val user = userService.getByUsername(username)
            ?: throw IllegalArgumentException("Cannot create password reset token for user '$username' because user does not exist")

        if (user.emailConfirmed == true) {
            throw IllegalStateException("Cannot create password reset token for user '$username' because self-service is enabled")
        }

        return createPasswordResetToken(user).token
    }


    fun resetPassword(token: String, newPassword: String): PasswordResetResult {
        val passwordResetToken =
            passwordResetTokenRepository.findByToken(token)
                ?: return PasswordResetResult.INVALID_TOKEN

        if (passwordResetToken.isExpired) {
            return PasswordResetResult.EXPIRED_TOKEN
        }

        val user = passwordResetToken.user

        userService.updatePassword(user, newPassword)
        passwordResetTokenRepository.delete(passwordResetToken)
        sessionService.logoutAllSessions(user)
        return PasswordResetResult.SUCCESS
    }
}