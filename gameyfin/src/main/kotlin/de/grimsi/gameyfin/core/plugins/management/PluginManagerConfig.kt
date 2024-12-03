package de.grimsi.gameyfin.core.plugins.management

import de.grimsi.gameyfin.core.plugins.config.PluginConfigRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PluginManagerConfig(
    private val pluginConfigRepository: PluginConfigRepository,
    private val dbPluginStatusProvider: DatabasePluginStatusProvider
) {
    private val log = KotlinLogging.logger {}

    @Bean
    fun gameyfinPluginManager(): GameyfinPluginManager {
        return GameyfinPluginManager(pluginConfigRepository, dbPluginStatusProvider)
    }

    /*@EventListener(ApplicationReadyEvent::class)
    fun loadPlugins() {
        gameyfinPluginManager().loadPlugins()
        gameyfinPluginManager().startPlugins()
        log.info { "Loaded plugins: ${gameyfinPluginManager().plugins.map { it.pluginId }}" }
    }*/
}