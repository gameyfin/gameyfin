package de.grimsi.gameyfin.core.plugins.config

import de.grimsi.gameyfin.core.plugins.management.GameyfinPluginManager
import de.grimsi.gameyfin.pluginapi.core.Configurable
import de.grimsi.gameyfin.pluginapi.core.PluginConfigElement
import io.github.oshai.kotlinlogging.KotlinLogging
import org.pf4j.PluginWrapper
import org.springframework.stereotype.Service

@Service
class PluginConfigService(
    private val pluginConfigRepository: PluginConfigRepository,
    private val pluginManager: GameyfinPluginManager
) {

    private val log = KotlinLogging.logger {}

    fun getConfigMetadata(pluginWrapper: PluginWrapper): List<PluginConfigElement> {
        log.debug { "Getting config metadata for plugin ${pluginWrapper.pluginId}" }
        val plugin = pluginWrapper.plugin
        if (plugin !is Configurable) return emptyList()
        return plugin.configMetadata
    }

    fun getConfig(pluginWrapper: PluginWrapper): Map<String, String?> {
        log.debug { "Getting config for plugin ${pluginWrapper.pluginId}" }
        return pluginConfigRepository.findAllById_PluginId(pluginWrapper.pluginId).associate { it.id.key to it.value }
    }

    fun updateConfig(pluginId: String, config: Map<String, String>) {
        log.debug { "Setting config entries for plugin $pluginId" }
        val entries = config.map { PluginConfigEntry(PluginConfigEntryKey(pluginId, it.key), it.value) }
        pluginConfigRepository.saveAll(entries)
        pluginManager.restart(pluginId)
    }
}