package de.grimsi.gameyfin.core.plugins.management

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import jakarta.annotation.security.RolesAllowed

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class PluginManagementEndpoint(
    private val pluginManagementService: PluginManagementService
) {
    fun getPlugins() = pluginManagementService.getPluginDtos()

    fun getPlugin(pluginId: String) = pluginManagementService.getPluginDto(pluginId)

    fun startPlugin(pluginId: String) = pluginManagementService.startPlugin(pluginId)

    fun stopPlugin(pluginId: String) = pluginManagementService.stopPlugin(pluginId)

    fun restartPlugin(pluginId: String) = pluginManagementService.restartPlugin(pluginId)

    fun enablePlugin(pluginId: String) = pluginManagementService.enablePlugin(pluginId)

    fun disablePlugin(pluginId: String) = pluginManagementService.disablePlugin(pluginId)

    fun validatePluginConfig(pluginId: String): Boolean = pluginManagementService.validatePluginConfig(pluginId)

    fun setPluginPriority(pluginId: String, priority: Int) =
        pluginManagementService.setPluginPriority(pluginId, priority)

    fun setPluginPriorities(pluginPriorities: Map<String, Int>) =
        pluginManagementService.setPluginPriorities(pluginPriorities)
}