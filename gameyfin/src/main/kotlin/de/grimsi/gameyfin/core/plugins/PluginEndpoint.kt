package de.grimsi.gameyfin.core.plugins

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.core.plugins.dto.PluginUpdateDto
import de.grimsi.gameyfin.pluginapi.core.PluginConfigValidationResult
import de.grimsi.gameyfin.users.util.isAdmin
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import reactor.core.publisher.Flux

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class PluginEndpoint(
    private val pluginService: PluginService
) {

    @PermitAll
    fun subscribe(): Flux<List<PluginUpdateDto>> {
        val user = SecurityContextHolder.getContext().authentication.principal as UserDetails
        return if (user.isAdmin()) PluginService.subscribe()
        else Flux.empty()
    }

    fun getAll() = pluginService.getAll()

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