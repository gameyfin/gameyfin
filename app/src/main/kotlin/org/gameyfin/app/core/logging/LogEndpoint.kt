package org.gameyfin.app.core.logging

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.security.isCurrentUserAdmin
import reactor.core.publisher.Flux

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class LogEndpoint(
    private val logService: LogService,
) {

    fun reloadLogConfig() {
        logService.configureFileLogging()
    }

    @PermitAll
    fun getApplicationLogs(): Flux<String> {
        return if (isCurrentUserAdmin()) logService.streamLogs()
        else Flux.empty()
    }
}