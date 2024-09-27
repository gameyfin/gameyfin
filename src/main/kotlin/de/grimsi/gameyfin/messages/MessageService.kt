package de.grimsi.gameyfin.messages

import de.grimsi.gameyfin.core.events.PasswordResetRequestEvent
import de.grimsi.gameyfin.core.events.RegistrationAttemptWithExistingEmailEvent
import de.grimsi.gameyfin.core.events.UserRegistrationEvent
import de.grimsi.gameyfin.core.events.UserRegistrationWaitingForApprovalEvent
import de.grimsi.gameyfin.messages.providers.AbstractMessageProvider
import de.grimsi.gameyfin.messages.templates.MessageTemplateService
import de.grimsi.gameyfin.messages.templates.MessageTemplates
import de.grimsi.gameyfin.users.UserService
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.util.*

@EnableAsync
@Service
class MessageService(
    private val applicationContext: ApplicationContext,
    private val templateService: MessageTemplateService,
    private val userService: UserService
) {

    val log: KLogger = KotlinLogging.logger {}

    val enabled: Boolean
        get() = providers.any { it.enabled }

    private val providers: List<AbstractMessageProvider>
        get() = applicationContext.getBeansOfType(AbstractMessageProvider::class.java).values.toList()

    fun testCredentials(provider: String, credentials: Map<String, Any>): Boolean {
        val notificationProvider = providers.find { it.providerKey == provider }
        val credentialsProperties = Properties().apply { putAll(credentials) }
        return notificationProvider?.testCredentials(credentialsProperties)
            ?: throw IllegalArgumentException("Provider '$provider' not found")
    }

    fun sendNotification(
        recipient: String,
        title: String,
        template: MessageTemplates,
        placeholders: Map<String, String>
    ) {
        providers.filter { it.enabled }.forEach {
            val content = templateService.fillMessageTemplate(template, it.supportedTemplateType, placeholders)
            it.sendNotification(recipient, title, content)
        }
    }

    /**
     * Sends a test notification.
     * Recipient is always the current user to prevent misuse.
     */
    fun sendTestNotification(templateKey: String, placeholders: Map<String, String>): Boolean {

        if (!enabled) {
            log.error { "No notification provider available, can't send test message" }
            return false
        }

        try {
            val auth: Authentication = SecurityContextHolder.getContext().authentication
            val user = userService.getByUsername(auth.name) ?: throw IllegalStateException("User not found")
            val template = templateService.getMessageTemplate(templateKey)
            sendNotification(user.email, "[Gameyfin] Test Notification", template, placeholders)
        } catch (e: Exception) {
            log.error(e) { "Failed to send test notification" }
            return false
        }

        return true
    }

    @Async
    @EventListener(PasswordResetRequestEvent::class)
    fun onPasswordResetRequest(event: PasswordResetRequestEvent) {

        if (!enabled) {
            log.error { "No notification provider available, can't send password reset message" }
            return
        }

        log.info { "Sending password reset request notification" }

        val token = event.token
        val resetLink = event.baseUrl + "/reset-password?token=${token.secret}"
        sendNotification(
            token.user.email,
            "[Gameyfin] Password Reset Request",
            MessageTemplates.PasswordResetRequest,
            mapOf("username" to token.user.username, "resetLink" to resetLink)
        )
    }

    @Async
    @EventListener(UserRegistrationWaitingForApprovalEvent::class)
    fun onUserRegistrationWaitingForApproval(event: UserRegistrationWaitingForApprovalEvent) {

        if (!enabled) {
            log.error { "No notification provider available, can't send 'waiting for approval' message" }
            return
        }

        log.info { "Sending waiting for approval notification" }

        val user = event.newUser
        sendNotification(
            user.email,
            "[Gameyfin] Waiting for Approval",
            MessageTemplates.WaitingForApproval,
            mapOf("username" to user.username)
        )
    }

    @Async
    @EventListener(UserRegistrationEvent::class)
    fun onUserRegistration(event: UserRegistrationEvent) {

        if (!enabled) {
            log.error { "No notification provider available, can't send registration message" }
            return
        }

        log.info { "Sending registration notification" }

        val user = event.newUser
        sendNotification(
            user.email,
            "[Gameyfin] Welcome",
            MessageTemplates.Welcome,
            mapOf("username" to user.username, "baseUrl" to event.baseUrl)
        )
    }

    @Async
    @EventListener(RegistrationAttemptWithExistingEmailEvent::class)
    fun onRegistrationAttemptWithExistingEmail(event: RegistrationAttemptWithExistingEmailEvent) {

        if (!enabled) {
            log.error { "No notification provider available, can't send 'registration attempt with existing email' message" }
            return
        }

        log.info { "Sending registration attempt with existing email notification" }

        val user = event.existingUser
        sendNotification(
            user.email,
            "[Gameyfin] Account alert",
            MessageTemplates.RegistrationAttemptWithExistingEmail,
            mapOf("username" to user.username, "passwordResetLink" to event.baseUrl)
        )
    }
}