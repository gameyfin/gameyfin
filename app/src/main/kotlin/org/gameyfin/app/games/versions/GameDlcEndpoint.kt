package org.gameyfin.app.games.versions

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role

@Endpoint
@AnonymousAllowed
class GameDlcEndpoint(private val dlcService: GameDlcService) {

    fun getIncluded(gameId: Long): List<GameIncludedDlcDto> = dlcService.getIncluded(gameId)

    @RolesAllowed(Role.Names.ADMIN)
    fun getCandidates(store: String, storeAppId: String): List<StoreDlcDto> =
        dlcService.getCandidates(store, storeAppId)

    @RolesAllowed(Role.Names.ADMIN)
    fun detectIncluded(gameId: Long, store: String, storeAppId: String): List<StoreDlcDto> =
        dlcService.detect(gameId, store, storeAppId)

    @RolesAllowed(Role.Names.ADMIN)
    fun setIncluded(linkId: Long, dlc: List<IncludedDlcDto>): GameIncludedDlcDto =
        dlcService.setIncluded(linkId, dlc)
}
