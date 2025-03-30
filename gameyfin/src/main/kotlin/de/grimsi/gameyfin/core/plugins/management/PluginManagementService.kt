package de.grimsi.gameyfin.core.plugins.management

import de.grimsi.gameyfin.pluginapi.core.GameyfinPlugin
import org.pf4j.ExtensionPoint
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.io.InputStream

@Service
class PluginManagementService(
    private val pluginManager: GameyfinPluginManager,
    private val pluginManagementRepository: PluginManagementRepository
) {
    fun getPluginDtos(): List<PluginDto> {
        return pluginManager.plugins.map {
            PluginDto(
                it.pluginId,
                it.descriptor.pluginDescription,
                it.descriptor.version,
                it.descriptor.provider,
                (it.plugin as GameyfinPlugin).hasLogo(),
                it.pluginState,
                getPluginManagementEntry(it.pluginId).priority
            )
        }
    }

    fun getPluginDto(pluginId: String): PluginDto {
        val plugin = pluginManager.getPlugin(pluginId)
        return PluginDto(
            plugin.pluginId,
            plugin.descriptor.pluginDescription,
            plugin.descriptor.version,
            plugin.descriptor.provider,
            (plugin.plugin as GameyfinPlugin).hasLogo(),
            plugin.pluginState,
            getPluginManagementEntry(pluginId).priority
        )
    }

    fun getPluginManagementEntry(pluginId: String): PluginManagementEntry {
        return pluginManagementRepository.findByIdOrNull(pluginId)
            ?: throw IllegalArgumentException("Plugin with ID $pluginId not found")
    }

    fun getPluginManagementEntry(clazz: Class<ExtensionPoint>): PluginManagementEntry {
        val pluginWrapper = pluginManager.whichPlugin(clazz)
        return pluginManagementRepository.findByIdOrNull(pluginWrapper.pluginId)
            ?: throw IllegalArgumentException("Plugin with class $clazz not found")
    }

    fun startPlugin(pluginId: String) {
        pluginManager.startPlugin(pluginId)
    }

    fun stopPlugin(pluginId: String) {
        pluginManager.stopPlugin(pluginId)
    }

    fun restartPlugin(pluginId: String) {
        pluginManager.restart(pluginId)
    }

    fun enablePlugin(pluginId: String) {
        pluginManager.enablePlugin(pluginId)
    }

    fun disablePlugin(pluginId: String) {
        pluginManager.disablePlugin(pluginId)
    }

    fun validatePluginConfig(pluginId: String): Boolean {
        return pluginManager.validatePluginConfig(pluginId)
    }

    fun setPluginPriority(pluginId: String, priority: Int) {
        val pluginManagementEntry = getPluginManagementEntry(pluginId)
        pluginManagementEntry.priority = priority
        pluginManagementRepository.save(pluginManagementEntry)
    }

    fun setPluginPriorities(pluginPriorities: Map<String, Int>) {
        pluginPriorities.forEach { (pluginId, priority) ->
            val pluginManagementEntry = getPluginManagementEntry(pluginId)
            pluginManagementEntry.priority = priority
            pluginManagementRepository.save(pluginManagementEntry)
        }
    }

    fun hasLogo(pluginId: String): Boolean {
        val plugin = pluginManager.getPlugin(pluginId).plugin as GameyfinPlugin
        return plugin.hasLogo()
    }

    fun getLogo(pluginId: String): InputStream? {
        val plugin = pluginManager.getPlugin(pluginId).plugin as GameyfinPlugin
        return plugin.getLogo()
    }
}