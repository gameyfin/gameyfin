package org.gameyfin.app.system

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role

@Endpoint
class SystemEndpoint(
    private val systemService: SystemService
) {

    @RolesAllowed(Role.Names.ADMIN)
    fun restart() {
        systemService.restart()
    }
}