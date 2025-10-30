package org.gameyfin.app.core.download.bandwidth

import com.vaadin.hilla.Endpoint
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.security.isCurrentUserAdmin
import reactor.core.publisher.Flux

/**
 * Endpoint for monitoring active download bandwidth usage.
 * Only accessible by administrators.
 */
@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class BandwidthMonitoringEndpoint(
    private val bandwidthMonitoringService: BandwidthMonitoringService
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @PermitAll
    fun subscribe(): Flux<List<List<SessionStatsDto>>> {
        return if (isCurrentUserAdmin()) BandwidthMonitoringService.subscribe()
        else Flux.empty()
    }

    fun getActiveSessions() = bandwidthMonitoringService.getActiveSessions()

    fun clearSession(sessionId: String) = bandwidthMonitoringService.clearSession(sessionId)
}

