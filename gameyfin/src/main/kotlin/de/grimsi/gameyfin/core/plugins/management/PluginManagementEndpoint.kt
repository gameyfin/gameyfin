package de.grimsi.gameyfin.core.plugins.management

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import jakarta.annotation.security.RolesAllowed

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class PluginManagementEndpoint(
    private val pluginManagementService: PluginManagementService
) {
    fun getPlugins() = pluginManagementService.getPlugins()

    fun getPlugin(pluginId: String) = pluginManagementService.getPlugin(pluginId)

    fun startPlugin(pluginId: String) = pluginManagementService.startPlugin(pluginId)

    fun stopPlugin(pluginId: String) = pluginManagementService.stopPlugin(pluginId)

    fun restartPlugin(pluginId: String) = pluginManagementService.restartPlugin(pluginId)

    fun enablePlugin(pluginId: String) = pluginManagementService.enablePlugin(pluginId)

    fun disablePlugin(pluginId: String) = pluginManagementService.disablePlugin(pluginId)

    fun validatePluginConfig(pluginId: String): Boolean = pluginManagementService.validatePluginConfig(pluginId)
}