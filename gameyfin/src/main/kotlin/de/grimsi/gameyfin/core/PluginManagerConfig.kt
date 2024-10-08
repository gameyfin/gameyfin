package de.grimsi.gameyfin.core

import org.pf4j.spring.SpringPluginManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PluginManagerConfig {
    @Bean
    fun pluginManager() = SpringPluginManager()
}