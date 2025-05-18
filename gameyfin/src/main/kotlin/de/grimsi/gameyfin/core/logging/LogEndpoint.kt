package de.grimsi.gameyfin.core.logging

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.users.util.isAdmin
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
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