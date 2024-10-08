package de.grimsi.gameyfin.system

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import jakarta.annotation.security.RolesAllowed

@Endpoint
class SystemEndpoint(
    private val systemService: SystemService
) {

    @RolesAllowed(Role.Names.ADMIN)
    fun restart() {
        systemService.restart()
    }
}