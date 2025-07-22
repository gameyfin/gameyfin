package org.gameyfin.app.core.plugins

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.plugins.dto.PluginUpdateDto
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import reactor.core.publisher.Flux

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class PluginEndpoint(
    private val pluginService: PluginService,
) {

    @PermitAll
    fun subscribe(): Flux<List<PluginUpdateDto>> {
        return if (isCurrentUserAdmin()) PluginService.subscribe()
        else Flux.empty()
    }

    fun getAll() = pluginService.getAll().sortedByDescending { it.priority }

    fun enablePlugin(pluginId: String) = pluginService.enablePlugin(pluginId)

    fun disablePlugin(pluginId: String) = pluginService.disablePlugin(pluginId)

    fun setPluginPriorities(pluginPriorities: Map<String, Int>) =
        pluginService.setPluginPriorities(pluginPriorities)

    fun validatePluginConfig(pluginId: String): PluginConfigValidationResult =
        pluginService.validatePluginConfig(pluginId, true)

    fun validateNewConfig(pluginId: String, config: Map<String, String>): PluginConfigValidationResult =
        pluginService.validatePluginConfig(pluginId, config)

    fun updateConfig(pluginId: String, updatedConfig: Map<String, String>) =
        pluginService.updateConfig(pluginId, updatedConfig)
}