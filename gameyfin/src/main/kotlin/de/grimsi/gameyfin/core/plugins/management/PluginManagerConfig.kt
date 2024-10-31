package de.grimsi.gameyfin.core.plugins.management

import de.grimsi.gameyfin.core.plugins.config.PluginConfigRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import java.nio.file.Path

@Configuration
class PluginManagerConfig(
    private val pluginConfigRepository: PluginConfigRepository
) {
    private val log = KotlinLogging.logger {}
    private val pluginPath = System.getProperty("pf4j.pluginsDir", "plugins")

    @Bean
    fun pluginManager() = GameyfinPluginManager(Path.of(pluginPath), pluginConfigRepository)

    @EventListener(ApplicationReadyEvent::class)
    fun loadPlugins() {
        pluginManager().loadPlugins()
        pluginManager().startPlugins()
        log.info { "Loaded plugins: ${pluginManager().plugins.map { it.pluginId }}" }
    }
}