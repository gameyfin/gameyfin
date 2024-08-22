package de.grimsi.gameyfin.system

import de.grimsi.gameyfin.config.Roles
import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed

@Endpoint
class SystemEndpoint(
    private val systemService: SystemService
) {

    @RolesAllowed(Roles.Names.SUPERADMIN, Roles.Names.ADMIN)
    fun restart() {
        systemService.restart()
    }
}