package de.grimsi.gameyfin.config

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.config.dto.ConfigEntryDto
import de.grimsi.gameyfin.config.dto.ConfigValuePairDto
import de.grimsi.gameyfin.core.Role
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import java.io.Serializable

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class ConfigEndpoint(
    private val config: ConfigService
) {

    /** CRUD endpoints for admins **/

    fun getAll(prefix: String?): List<ConfigEntryDto> {
        return config.getAll(prefix)
    }

    fun get(key: String): Serializable? {
        return config.get(key)
    }

    fun set(key: String, value: String) {
        config.set(key, value)
    }

    fun setAll(configs: List<ConfigValuePairDto>) {
        config.setAll(configs)
    }

    fun resetConfig(key: String) {
        config.deleteConfig(key)
    }

    fun deleteConfig(key: String) {
        config.deleteConfig(key)
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
