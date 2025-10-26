package org.gameyfin.app.platforms

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.platforms.dto.PlatformStatsDto
import reactor.core.publisher.Flux

@Endpoint
@DynamicPublicAccess
@AnonymousAllowed
class PlatformEndpoint(
    private val platformService: PlatformService
) {
    fun subscribe(): Flux<out List<PlatformStatsDto>> {
        return PlatformService.subscribe()
    }

    /**
     * Returns the list of all platforms supported by at least one active metadata plugin.
     */
    fun getStats(): PlatformStatsDto = platformService.getStats()
}