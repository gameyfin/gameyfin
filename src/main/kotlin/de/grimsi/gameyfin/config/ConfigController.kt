package de.grimsi.gameyfin.config

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.config.dto.ConfigEntryDto
import de.grimsi.gameyfin.meta.Roles
import jakarta.annotation.security.RolesAllowed

@Endpoint
@RolesAllowed(Roles.Names.SUPERADMIN, Roles.Names.ADMIN)
class ConfigController(
    private val appConfigService: ConfigService
) {

    fun getConfigs(prefix: String?): List<ConfigEntryDto> {
        return appConfigService.getAllConfigValues(prefix)
    }

    fun getConfig(key: String): String {
        return appConfigService.getConfigValue(key)
    }

    fun setConfig(key: String, value: String) {
        appConfigService.setConfigValue(key, value)
    }

    fun resetConfig(key: String) {
        appConfigService.resetConfigValue(key)
    }

    fun deleteConfig(key: String) {
        appConfigService.deleteConfig(key)
    }
}
