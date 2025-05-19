package de.grimsi.gameyfin.config

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.config.dto.ConfigEntryDto
import de.grimsi.gameyfin.config.dto.ConfigUpdateDto
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.users.util.isAdmin
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
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
    fun subscribe(): Flux<ConfigUpdateDto> {
        val user = SecurityContextHolder.getContext().authentication.principal as UserDetails
        return if (user.isAdmin()) configService.subscribe()
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
