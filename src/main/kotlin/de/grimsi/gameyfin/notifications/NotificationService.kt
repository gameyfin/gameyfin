package de.grimsi.gameyfin.notifications

import de.grimsi.gameyfin.core.events.PasswordResetRequestEvent
import de.grimsi.gameyfin.notifications.providers.AbstractNotificationProvider
import de.grimsi.gameyfin.notifications.templates.MessageTemplateService
import de.grimsi.gameyfin.notifications.templates.MessageTemplates
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.stereotype.Service
import java.util.*

@EnableAsync
@Service
class NotificationService(
    private val applicationContext: ApplicationContext,
    private val templateService: MessageTemplateService
) {

    val log: KLogger = KotlinLogging.logger {}

    val enabled: Boolean
        get() = providers.any { it.enabled }

    private val providers: List<AbstractNotificationProvider>
        get() = applicationContext.getBeansOfType(AbstractNotificationProvider::class.java).values.toList()

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

    @Async
    @EventListener(PasswordResetRequestEvent::class)
    fun onPasswordResetRequest(event: PasswordResetRequestEvent) {

        if (!enabled) {
            log.error { "No notification provider available, can't send password reset message" }
            return
        }

        log.info { "Sending password reset request notification" }

        val token = event.token
        val resetLink = event.baseUrl + "/reset-password?token=${token.token}"
        sendNotification(
            token.user.email,
            "[Gameyfin] Password Reset Request",
            MessageTemplates.PasswordResetRequest,
            mapOf("username" to token.user.username, "resetLink" to resetLink)
        )
    }
}