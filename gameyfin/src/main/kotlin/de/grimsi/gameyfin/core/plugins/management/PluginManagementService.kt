package de.grimsi.gameyfin.core.plugins.management

import org.pf4j.ExtensionPoint
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

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
                it.pluginState
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
            plugin.pluginState
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
}