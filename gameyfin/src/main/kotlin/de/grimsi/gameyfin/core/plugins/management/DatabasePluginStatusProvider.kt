package de.grimsi.gameyfin.core.plugins.management

import org.pf4j.PluginStatusProvider
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class DatabasePluginStatusProvider(
    private val pluginManagementRepository: PluginManagementRepository
) : PluginStatusProvider {

    override fun isPluginDisabled(pluginId: String): Boolean {
        var pluginManagement = pluginManagementRepository.findByIdOrNull(pluginId)

        // If the plugin is unknown, persist it as enabled
        if(pluginManagement == null) {
            pluginManagement = pluginManagementRepository.save(PluginManagementEntry(pluginId = pluginId, enabled = true))
        }

        return pluginManagement.enabled != true
    }

    override fun disablePlugin(pluginId: String) {
        val pluginManagement = pluginManagementRepository.findByIdOrNull(pluginId)
        if (pluginManagement != null) {
            pluginManagement.enabled = false
            pluginManagementRepository.save(pluginManagement)
        }
    }

    override fun enablePlugin(pluginId: String) {
        val pluginManagement = pluginManagementRepository.findByIdOrNull(pluginId)
        if (pluginManagement != null) {
            pluginManagement.enabled = true
            pluginManagementRepository.save(pluginManagement)
        }
    }
}