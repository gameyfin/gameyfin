package de.grimsi.gameyfin.notifications.providers

import de.grimsi.gameyfin.config.ConfigService
import java.util.*

abstract class AbstractNotificationProvider(
    val providerKey: String,
    private val config: ConfigService
) {
    private val configKey = String.format("notifications.providers.%s.enabled", providerKey)

    fun isEnabled(): Boolean {
        return config.get(configKey).toBoolean()
    }

    abstract fun testCredentials(credentials: Properties): Boolean

    abstract fun sendNotification(recipient: String, title: String, message: String)

}