package de.grimsi.gameyfin.core.plugins.config

import de.grimsi.gameyfin.core.plugins.management.GameyfinPluginManager
import de.grimsi.gameyfin.pluginapi.core.GameyfinPlugin
import de.grimsi.gameyfin.pluginapi.core.PluginConfigElement
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class PluginConfigService(
    private val pluginConfigRepository: PluginConfigRepository,
    private val pluginManager: GameyfinPluginManager
) {

    private val log = KotlinLogging.logger {}

    fun getConfigMetadata(pluginId: String): List<PluginConfigElement> {
        log.info { "Getting config metadata for plugin $pluginId" }

        val plugin = try {
            pluginManager.getPlugin(pluginId).plugin
        } catch (_: NoClassDefFoundError) {
            return emptyList()
        }

        if (plugin !is GameyfinPlugin) return emptyList()

        return plugin.configMetadata
    }

    fun getConfig(pluginId: String): Map<String, String?> {
        log.info { "Getting config for plugin $pluginId" }
        return pluginConfigRepository.findAllById_PluginId(pluginId).associate { it.id.key to it.value }
    }

    fun setConfigEntries(pluginId: String, config: Map<String, String>) {
        log.info { "Setting config entries for plugin $pluginId" }
        val entries = config.map { PluginConfigEntry(PluginConfigEntryKey(pluginId, it.key), it.value) }
        pluginConfigRepository.saveAll(entries)
        pluginManager.restart(pluginId)
    }

    fun setConfigEntry(pluginId: String, key: String, value: String) {
        log.info { "Setting config entry $key for plugin $pluginId" }
        val entry = PluginConfigEntry(PluginConfigEntryKey(pluginId, key), value)
        pluginConfigRepository.save(entry)
        pluginManager.restart(pluginId)
    }
}