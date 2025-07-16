package org.gameyfin.app.config

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.config.dto.ConfigEntryDto
import org.gameyfin.app.config.dto.ConfigUpdateDto
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.util.isAdmin
import reactor.core.publisher.Flux

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class ConfigEndpoint(
    private val configService: ConfigService,
    private val userService: UserService,
) {
    companion object {
        val log = KotlinLogging.logger { }
    }

    /** CRUD endpoints for admins **/

    @PermitAll
    fun subscribe(): Flux<List<ConfigUpdateDto>> {
        val user = userService.getCurrentUser()
        return if (user.isAdmin()) ConfigService.subscribe()
        else Flux.empty()
    }

    fun getAll(): List<ConfigEntryDto> = configService.getAll()

    fun update(update: ConfigUpdateDto) = configService.update(update)

    /** Specific read-only endpoint for all users **/

    @DynamicPublicAccess
    @AnonymousAllowed
    fun isSsoEnabled(): Boolean = configService.get(ConfigProperties.SSO.OIDC.Enabled) == true

    @DynamicPublicAccess
    @AnonymousAllowed
    fun getSsoLogoutUrl(): String? = configService.get(ConfigProperties.SSO.OIDC.LogoutUrl)

    @DynamicPublicAccess
    @AnonymousAllowed
    fun isPublicAccessEnabled(): Boolean = configService.get(ConfigProperties.Libraries.AllowPublicAccess) == true

}
