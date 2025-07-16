package org.gameyfin.app.system

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class SystemEndpoint(
    private val systemService: SystemService
) {
    fun restart() {
        systemService.restart()
    }
}