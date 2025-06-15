package org.gameyfin.app.config

import com.vaadin.hilla.Endpoint
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.config.dto.ConfigEntryDto
import org.gameyfin.app.config.dto.ConfigUpdateDto
import org.gameyfin.app.core.Role
import org.gameyfin.app.users.util.isAdmin
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import reactor.core.publisher.Flux

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class ConfigEndpoint(
    private val configService: ConfigService
) {
    companion object {
        val log = KotlinLogging.logger { }
    }

    /** CRUD endpoints for admins **/

    @PermitAll
    fun subscribe(): Flux<List<ConfigUpdateDto>> {
        val user = SecurityContextHolder.getContext().authentication.principal as UserDetails
        return if (user.isAdmin()) ConfigService.subscribe()
        else Flux.empty()
    }

    fun getAll(): List<ConfigEntryDto> = configService.getAll()

    fun update(update: ConfigUpdateDto) = configService.update(update)

    /** Specific read-only endpoint for all users **/

    @PermitAll
    fun isSsoEnabled(): Boolean? = configService.get(ConfigProperties.SSO.OIDC.Enabled)

    @PermitAll
    fun getLogoutUrl(): String? = configService.get(ConfigProperties.SSO.OIDC.LogoutUrl)
}
