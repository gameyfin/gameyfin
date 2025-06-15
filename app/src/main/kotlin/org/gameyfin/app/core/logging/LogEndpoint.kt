package org.gameyfin.app.core.logging

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.users.util.isAdmin
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import reactor.core.publisher.Flux

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class LogEndpoint(
    private val logService: LogService
) {

    fun reloadLogConfig() {
        logService.configureFileLogging()
    }

    @PermitAll
    fun getApplicationLogs(): Flux<String> {
        val user = SecurityContextHolder.getContext().authentication.principal as UserDetails
        return if (user.isAdmin()) logService.streamLogs()
        else Flux.empty()
    }
}