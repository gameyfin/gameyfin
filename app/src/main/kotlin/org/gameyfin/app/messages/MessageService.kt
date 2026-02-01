package org.gameyfin.app.messages

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.events.*
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.messages.providers.AbstractMessageProvider
import org.gameyfin.app.messages.templates.MessageTemplateService
import org.gameyfin.app.messages.templates.MessageTemplates
import org.gameyfin.app.users.UserService
import org.springframework.beans.factory.getBeansOfType
import org.springframework.context.ApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.*

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
        get() = applicationContext.getBeansOfType<AbstractMessageProvider>().values.toList()

    fun testCredentials(provider: String, credentials: Map<String, Any>): Boolean {
        val messageProvider = providers.find { it.providerKey == provider }
        val credentialsProperties = Properties().apply { putAll(credentials) }
        return messageProvider?.testCredentials(credentialsProperties)
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
     * Sends a test message.
     * Recipient is always the current user to prevent misuse.
     */
    fun sendTestNotification(templateKey: String, placeholders: Map<String, String>): Boolean {

        if (!enabled) {
            log.error { "No message provider available, can't send test message" }
            return false
        }

        try {
            val auth = getCurrentAuth() ?: throw IllegalStateException("No authentication found")
            val user = userService.getByUsername(auth.name) ?: throw IllegalStateException("User not found")
            val template = templateService.getMessageTemplate(templateKey)
            sendNotification(user.email, "[Gameyfin] Test Notification", template, placeholders)
        } catch (e: Exception) {
            log.error { "Failed to send test message: ${e.message}" }
            log.debug(e) {}
            return false
        }

        return true
    }

    @Async
    @EventListener(PasswordResetRequestEvent::class)
    fun onPasswordResetRequest(event: PasswordResetRequestEvent) {

        if (!enabled) {
            log.error { "No message provider available, can't send password reset message" }
            return
        }

        log.info { "Sending password reset request message" }

        val token = event.token
        val resetLink = event.baseUrl + "/reset-password?token=${token.secret}"
        sendNotification(
            token.creator.email,
            "[Gameyfin] Password Reset Request",
            MessageTemplates.PasswordResetRequest,
            mapOf("username" to token.creator.username, "resetLink" to resetLink)
        )
    }

    @Async
    @EventListener(UserRegistrationWaitingForApprovalEvent::class)
    fun onUserRegistrationWaitingForApproval(event: UserRegistrationWaitingForApprovalEvent) {

        if (!enabled) {
            log.error { "No message provider available, can't send 'waiting for approval' message" }
            return
        }

        log.info { "Sending waiting for approval message" }

        val user = event.newUser
        sendNotification(
            user.email,
            "[Gameyfin] Waiting for Approval",
            MessageTemplates.WaitingForApproval,
            mapOf("username" to user.username)
        )
    }

    @Async
    @EventListener(AccountStatusChangedEvent::class)
    fun onAccountStatusChanged(event: AccountStatusChangedEvent) {

        if (!enabled) {
            log.error { "No message provider available, can't send registration message" }
            return
        }

        log.info { "Sending registration message" }

        val user = event.user

        if (event.user.enabled) {
            sendNotification(
                user.email,
                "[Gameyfin] Your account has been enabled",
                MessageTemplates.AccountEnabled,
                mapOf("username" to user.username, "baseUrl" to event.baseUrl)
            )
        } else {
            sendNotification(
                user.email,
                "[Gameyfin] Your account has been disabled",
                MessageTemplates.AccountDisabled,
                mapOf("username" to user.username, "baseUrl" to event.baseUrl)
            )
        }
    }

    @Async
    @EventListener(RegistrationAttemptWithExistingEmailEvent::class)
    fun onRegistrationAttemptWithExistingEmail(event: RegistrationAttemptWithExistingEmailEvent) {

        if (!enabled) {
            log.error { "No message provider available, can't send 'registration attempt with existing email' message" }
            return
        }

        log.info { "Sending registration attempt with existing email message" }

        val user = event.existingUser
        sendNotification(
            user.email,
            "[Gameyfin] Account alert",
            MessageTemplates.RegistrationAttemptWithExistingEmail,
            mapOf("username" to user.username, "passwordResetLink" to event.baseUrl)
        )
    }

    @Async
    @EventListener(EmailNeedsConfirmationEvent::class)
    fun onEmailNeedsConfirmation(event: EmailNeedsConfirmationEvent) {

        if (!enabled) {
            log.error { "No message provider available, can't send email confirmation message" }
            return
        }

        log.info { "Sending email confirmation message" }

        val user = event.token.creator
        val confirmationLink = event.baseUrl + "/confirm-email?token=${event.token.secret}"
        sendNotification(
            user.email,
            "[Gameyfin] Email Confirmation",
            MessageTemplates.EmailConfirmation,
            mapOf("username" to user.username, "confirmationLink" to confirmationLink)
        )
    }

    @Async
    @EventListener(UserInvitationEvent::class)
    fun onUserInvitation(event: UserInvitationEvent) {

        if (!enabled) {
            log.error { "No message provider available, can't send invitation message" }
            return
        }

        log.info { "Sending invitation message" }

        val invitationLink = event.baseUrl + "/accept-invitation?token=${event.token.secret}"
        sendNotification(
            event.email,
            "[Gameyfin] You've been invited!",
            MessageTemplates.UserInvitation,
            mapOf("invitationLink" to invitationLink)
        )
    }

    @Async
    @EventListener(UserDeletedEvent::class)
    fun onAccountDeletion(event: UserDeletedEvent) {

        if (!enabled) {
            log.error { "No message provider available, can't send account deletion message" }
            return
        }

        log.info { "Sending account deletion message" }

        sendNotification(
            event.user.email,
            "[Gameyfin] Your account has been deleted",
            MessageTemplates.AccountDeleted,
            mapOf("username" to event.user.username, "baseUrl" to event.baseUrl)
        )
    }
}