package de.grimsi.gameyfin.config

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.config.dto.ConfigEntryDto
import de.grimsi.gameyfin.config.dto.ConfigUpdateDto
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.users.util.isAdmin
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class ConfigEndpoint(
    private val config: ConfigService
) {

    /** CRUD endpoints for admins **/
    private val configUpdates = Sinks.many().multicast().onBackpressureBuffer<ConfigUpdateDto>()

    fun getAll(): List<ConfigEntryDto> {
        return config.getAll(null)
    }

    @PermitAll
    fun subscribe(): Flux<ConfigUpdateDto> {
        val user = SecurityContextHolder.getContext().authentication.principal as UserDetails
        return if (user.isAdmin()) configUpdates.asFlux()
        else Flux.empty()
    }

    fun update(update: ConfigUpdateDto) {
        config.update(update.updates)
        configUpdates.tryEmitNext(update)
    }

    /** Specific read-only endpoint for all users **/

    @PermitAll
    fun isSsoEnabled(): Boolean? {
        return config.get(ConfigProperties.SSO.OIDC.Enabled)
    }

    @PermitAll
    fun getLogoutUrl(): String? {
        return config.get(ConfigProperties.SSO.OIDC.LogoutUrl)
    }
}
