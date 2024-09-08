package de.grimsi.gameyfin.system

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.meta.Roles
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