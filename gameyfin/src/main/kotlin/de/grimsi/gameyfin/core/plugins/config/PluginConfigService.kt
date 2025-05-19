package de.grimsi.gameyfin.core.plugins.config

import de.grimsi.gameyfin.core.plugins.management.GameyfinPluginManager
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class PluginConfigService(
    private val pluginConfigRepository: PluginConfigRepository,
    private val pluginManager: GameyfinPluginManager
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }


}