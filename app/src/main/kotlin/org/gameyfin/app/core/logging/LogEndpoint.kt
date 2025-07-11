package org.gameyfin.app.core.logging

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.util.isAdmin
import reactor.core.publisher.Flux

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class LogEndpoint(
    private val logService: LogService,
    private val userService: UserService,
) {

    fun reloadLogConfig() {
        logService.configureFileLogging()
    }

    @PermitAll
    fun getApplicationLogs(): Flux<String> {
        val user = userService.getCurrentUser()
        return if (user.isAdmin()) logService.streamLogs()
        else Flux.empty()
    }
}