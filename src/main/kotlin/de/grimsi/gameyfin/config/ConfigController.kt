package de.grimsi.gameyfin.config

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.meta.Roles
import jakarta.annotation.security.RolesAllowed

@Endpoint
@RolesAllowed(Roles.Names.SUPERADMIN, Roles.Names.ADMIN)
class ConfigController(
    private val appConfigService: ConfigService
) {

    fun getConfig(key: String): String {
        return appConfigService.getConfigValue(key)
    }

    fun setConfig(config: Pair<String, String>) {
        appConfigService.setConfigValue(config.first, config.second)
    }

    fun resetConfig(key: String) {
        appConfigService.resetConfigValue(key)
    }

    fun deleteConfig(key: String) {
        appConfigService.deleteConfig(key)
    }
}
