package de.grimsi.gameyfin.core.plugins

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.core.plugins.config.PluginConfigValidationResult
import de.grimsi.gameyfin.core.plugins.dto.PluginUpdateDto
import de.grimsi.gameyfin.core.plugins.management.PluginManagementService
import de.grimsi.gameyfin.users.util.isAdmin
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import reactor.core.publisher.Flux

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class PluginEndpoint(
    private val pluginManagementService: PluginManagementService
) {

    @PermitAll
    fun subscribe(): Flux<PluginUpdateDto> {
        val user = SecurityContextHolder.getContext().authentication.principal as UserDetails
        return if (user.isAdmin()) pluginManagementService.subscribe()
        else Flux.empty()
    }

    fun getAll() = pluginManagementService.getAll()

    fun enablePlugin(pluginId: String) = pluginManagementService.enablePlugin(pluginId)

    fun disablePlugin(pluginId: String) = pluginManagementService.disablePlugin(pluginId)

    fun setPluginPriorities(pluginPriorities: Map<String, Int>) =
        pluginManagementService.setPluginPriorities(pluginPriorities)

    fun validatePluginConfig(pluginId: String): PluginConfigValidationResult =
        pluginManagementService.validatePluginConfig(pluginId)

    fun updateConfig(pluginId: String, updatedConfig: Map<String, String>) =
        pluginManagementService.updateConfig(pluginId, updatedConfig)
}