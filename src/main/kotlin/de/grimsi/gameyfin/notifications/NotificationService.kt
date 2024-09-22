package de.grimsi.gameyfin.notifications

import de.grimsi.gameyfin.core.events.PasswordResetRequestEvent
import de.grimsi.gameyfin.notifications.providers.AbstractNotificationProvider
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.*

@Service
class NotificationService(
    private val applicationContext: ApplicationContext
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

    fun sendNotification(recipient: String, title: String, message: String) {
        providers.filter { it.enabled }.forEach { it.sendNotification(recipient, title, message) }
    }

    @Async
    @EventListener(PasswordResetRequestEvent::class)
    fun onPasswordResetRequest(event: PasswordResetRequestEvent) {
        log.info { "Sending password reset request notification" }

        val token = event.token

        // TODO: Implement proper email template
        sendNotification(
            token.user.email,
            "Password Reset Request",
            "You have requested a password reset. Your token is ${token.token}"
        )
    }
}