package org.gameyfin.app.requests

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.requests.dto.GameRequestCreationDto
import org.gameyfin.app.requests.dto.GameRequestEvent
import org.gameyfin.app.requests.status.GameRequestStatus
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux

@Endpoint
@Service
@DynamicPublicAccess
@AnonymousAllowed
class GameRequestEndpoint(
    private val gameRequestService: GameRequestService
) {

    fun subscribe(): Flux<List<GameRequestEvent>> {
        return GameRequestService.subscribe()
    }

    fun getAll() = gameRequestService.getAll()

    fun create(gameRequest: GameRequestCreationDto) {
        gameRequestService.createRequest(gameRequest)
    }

    @PermitAll
    fun toggleVote(gameRequestId: Long) {
        gameRequestService.toggleRequestVote(gameRequestId)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun changeStatus(gameRequestId: Long, newStatus: GameRequestStatus) {
        gameRequestService.changeRequestStatus(gameRequestId, newStatus)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun delete(gameRequestId: Long) {
        gameRequestService.deleteRequest(gameRequestId)
    }
}