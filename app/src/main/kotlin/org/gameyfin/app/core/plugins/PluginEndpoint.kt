package org.gameyfin.app.core.plugins

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.core.plugins.dto.PluginUpdateDto
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import reactor.core.publisher.Flux

@Endpoint
@DynamicPublicAccess
@AnonymousAllowed
class PluginEndpoint(
    private val pluginService: PluginService,
) {

    fun subscribe(): Flux<List<PluginUpdateDto>> {
        return PluginService.subscribe()
    }

    fun getAll() = pluginService.getAll().sortedByDescending { it.priority }

    @RolesAllowed(Role.Names.ADMIN)
    fun enablePlugin(pluginId: String) = pluginService.enablePlugin(pluginId)

    @RolesAllowed(Role.Names.ADMIN)
    fun disablePlugin(pluginId: String) = pluginService.disablePlugin(pluginId)

    @RolesAllowed(Role.Names.ADMIN)
    fun setPluginPriorities(pluginPriorities: Map<String, Int>) =
        pluginService.setPluginPriorities(pluginPriorities)

    @RolesAllowed(Role.Names.ADMIN)
    fun validatePluginConfig(pluginId: String): PluginConfigValidationResult =
        pluginService.validatePluginConfig(pluginId, true)

    @RolesAllowed(Role.Names.ADMIN)
    fun validateNewConfig(pluginId: String, config: Map<String, String>): PluginConfigValidationResult =
        pluginService.validatePluginConfig(pluginId, config)

    @RolesAllowed(Role.Names.ADMIN)
    fun updateConfig(pluginId: String, updatedConfig: Map<String, String>) =
        pluginService.updateConfig(pluginId, updatedConfig)
}