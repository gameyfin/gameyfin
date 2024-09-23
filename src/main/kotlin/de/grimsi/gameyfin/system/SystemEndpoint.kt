package de.grimsi.gameyfin.system

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Roles
import jakarta.annotation.security.RolesAllowed

@Endpoint
class SystemEndpoint(
    private val systemService: SystemService
) {

    @RolesAllowed(Roles.Names.ADMIN)
    fun restart() {
        systemService.restart()
    }
}