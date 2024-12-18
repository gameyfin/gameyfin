package de.grimsi.gameyfin.core.plugins.management

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener

@Configuration
class PluginManagerConfig(
    private val pluginManager: GameyfinPluginManager
) {
    private val log = KotlinLogging.logger {}

    @EventListener(ApplicationReadyEvent::class)
    fun loadPlugins() {
        pluginManager.loadPlugins()
        pluginManager.startPlugins()
        log.info { "Loaded plugins: ${pluginManager.plugins.map { it.pluginId }}" }
    }
}