package de.grimsi.gameyfin.core

import org.pf4j.DefaultPluginManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

@Configuration
class PluginManagerConfig {
    private val pluginPath = Path.of("plugins")

    @Bean
    fun pluginManager() = DefaultPluginManager(pluginPath)
}