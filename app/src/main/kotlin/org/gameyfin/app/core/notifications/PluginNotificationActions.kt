package org.gameyfin.app.core.notifications

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.pluginapi.notifications.NotificationActionContext
import org.gameyfin.pluginapi.notifications.NotificationActionHandler
import org.gameyfin.pluginapi.notifications.NotificationActionPermission
import org.pf4j.PluginState
import org.pf4j.PluginStateEvent
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/** Stable persisted keys refer to callbacks only while their plugin is started. */
@Component
class PluginNotificationActions(
    private val manager: ObjectProvider<GameyfinPluginManager>,
    private val store: NotificationStore
) {
    private class Registration(val handlers: Map<String, NotificationActionHandler>) {
        var active = true
    }

    private val registrations = ConcurrentHashMap<String, Registration>()

    @PostConstruct
    fun initialize() {
        manager.getObject().startedPlugins.forEach { register(it.pluginId) }
    }

    @EventListener
    fun pluginChanged(event: PluginStateEvent) {
        val id = event.plugin.pluginId
        unregister(id)
        if (event.pluginState == PluginState.STARTED) register(id)
        store.invalidatePlugin(id)
    }

    private fun register(id: String) {
        try {
            val handlers: List<NotificationActionHandler> =
                manager.getObject().getExtensions(NotificationActionHandler::class.java, id)
            require(handlers.map { it.key }
                .distinct().size == handlers.size) { "Duplicate notification action in plugin $id" }
            registrations[id] = Registration(handlers.associateBy { it.key })
        } catch (e: Throwable) {
            KotlinLogging.logger {}.warn(e) { "Could not register notification actions for plugin $id" }
        }
    }

    private fun unregister(id: String) {
        registrations.remove(id)?.let { synchronized(it) { it.active = false } }
    }

    fun available(source: String, key: String, admin: Boolean): Boolean {
        val registration = registrations[source.removePrefix("plugin:")] ?: return false
        val handler = registration.handlers[key] ?: return false
        return registration.active && (admin || handler.permission == NotificationActionPermission.RECIPIENT)
    }

    fun execute(source: String, key: String, admin: Boolean, context: NotificationActionContext) {
        val registration = registrations[source.removePrefix("plugin:")] ?: error("Plugin action is unavailable")
        synchronized(registration) {
            check(registration.active) { "Plugin action is unavailable" }
            val handler = registration.handlers[key] ?: error("Plugin action is unavailable")
            check(admin || handler.permission == NotificationActionPermission.RECIPIENT) { "Admin permission required" }
            handler.execute(context)
        }
    }
}
