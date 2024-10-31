package de.grimsi.gameyfin.core.plugins.config

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.pluginapi.core.PluginConfigElement
import jakarta.annotation.security.RolesAllowed

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class PluginConfigEndpoint(
    private val pluginConfigService: PluginConfigService
) {
    fun getConfigMetadata(pluginId: String): List<PluginConfigElement> {
        return pluginConfigService.getConfigMetadata(pluginId)
    }

    fun getConfig(pluginId: String): Map<String, String?> {
        return pluginConfigService.getConfig(pluginId)
    }

    fun setConfigEntries(pluginId: String, config: Map<String, String>) {
        pluginConfigService.setConfigEntries(pluginId, config)
    }

    fun setConfigEntry(pluginId: String, key: String, value: String) {
        pluginConfigService.setConfigEntry(pluginId, key, value)
    }
}