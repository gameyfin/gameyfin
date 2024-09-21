package de.grimsi.gameyfin.notifications

import de.grimsi.gameyfin.notifications.providers.AbstractNotificationProvider
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import java.util.*

@Service
class NotificationService(
    private val applicationContext: ApplicationContext
) {

    private val providers: List<AbstractNotificationProvider>
        get() = applicationContext.getBeansOfType(AbstractNotificationProvider::class.java).values.toList()

    fun testCredentials(provider: String, credentials: Map<String, Any>): Boolean {
        val notificationProvider = providers.find { it.providerKey == provider }
        val credentialsProperties = Properties().apply { putAll(credentials) }
        return notificationProvider?.testCredentials(credentialsProperties)
            ?: throw IllegalArgumentException("Provider $provider not found")
    }
}