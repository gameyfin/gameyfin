package de.grimsi.gameyfin.core.plugins

import de.grimsi.gameyfin.pluginapi.core.GameyfinPlugin
import de.grimsi.gameyfin.pluginapi.core.PluginConfigElement
import org.springframework.stereotype.Service

@Service
class PluginConfigService(
    private val pluginConfigRepository: PluginConfigRepository,
    private val pluginManager: SpringDevtoolsPluginManager
) {

    fun getConfigMetadata(pluginId: String): List<PluginConfigElement> {
        val plugin = pluginManager.getPlugin(pluginId).plugin as GameyfinPlugin
        return plugin.getConfigMetadata()
    }

    fun getConfig(pluginId: String): Map<String, String?> {
        return pluginConfigRepository.findAllById_PluginId(pluginId).associate { it.id.key to it.value }
    }

    fun setConfigEntries(pluginId: String, config: Map<String, String>) {
        val entries = config.map { PluginConfigEntry(PluginConfigEntryKey(pluginId, it.key), it.value) }
        pluginConfigRepository.saveAll(entries)
        pluginManager.restart(pluginId)
    }

    fun setConfigEntry(pluginId: String, key: String, value: String) {
        val entry = PluginConfigEntry(PluginConfigEntryKey(pluginId, key), value)
        pluginConfigRepository.save(entry)
        pluginManager.restart(pluginId)
    }
}