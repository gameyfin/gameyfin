package de.grimsi.gameyfin.users

import de.grimsi.gameyfin.core.Utils
import de.grimsi.gameyfin.core.events.PasswordResetRequestEvent
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

    fun requestPasswordReset(email: String) {

        log.info { "Initiating password reset request for '${Utils.maskEmail(email)}'" }

        val user = userService.getByEmail(email)

        // A user can only reset its password if its email is confirmed, and it's not an SSO user
        if (user != null && user.emailConfirmed && user.oidcProviderId == null) {
            val token = createPasswordResetToken(user)
            eventPublisher.publishEvent(PasswordResetRequestEvent(this, token, baseUrl))
        }

        // Simulate a delay to prevent timing attacks
        Thread.sleep(secureRandom.nextLong(1024))
    }

    fun createPasswordResetToken(user: User): PasswordResetToken {
        val token = PasswordResetToken(
            user = user,
            token = UUID.randomUUID().toString()
        )

        passwordResetTokenRepository.findByUser(user)?.let {
            passwordResetTokenRepository.delete(it)
        }

        return passwordResetTokenRepository.save(token)
    }

    fun resetPassword(token: String, newPassword: String) {
        val passwordResetToken =
            passwordResetTokenRepository.findByToken(token)
                ?: throw IllegalArgumentException("Token not found")

        if (passwordResetToken.isExpired) {
            throw IllegalStateException("Token is expired")
        }

        val user = passwordResetToken.user

        userService.updatePassword(user, newPassword)
        passwordResetTokenRepository.delete(passwordResetToken)
        sessionService.logoutAllSessions(user)
    }
}