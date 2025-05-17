package de.grimsi.gameyfin.config

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.config.dto.ConfigEntryDto
import de.grimsi.gameyfin.config.dto.ConfigUpdateDto
import de.grimsi.gameyfin.core.Role
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
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

    // FIXME
    @AnonymousAllowed
    fun subscribe(): Flux<ConfigUpdateDto> = configUpdates.asFlux()

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
