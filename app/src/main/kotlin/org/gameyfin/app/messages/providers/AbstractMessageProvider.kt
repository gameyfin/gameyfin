package org.gameyfin.app.messages.providers

import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.messages.templates.TemplateType
import java.util.*

abstract class AbstractMessageProvider(
    val providerKey: String,
    val supportedTemplateType: TemplateType,
    protected val config: ConfigService
) {
    protected companion object {
        const val BASE_KEY = "messages.providers"
    }

    private val configKey = String.format("%s.%s.enabled", BASE_KEY, providerKey)

    val enabled: Boolean
        get() = config.get(configKey) as Boolean

    abstract fun testCredentials(credentials: Properties): Boolean

    abstract fun sendNotification(recipient: String, title: String, message: String)
}