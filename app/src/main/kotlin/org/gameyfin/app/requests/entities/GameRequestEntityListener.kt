package org.gameyfin.app.requests.entities

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import org.gameyfin.app.requests.GameRequestService
import org.gameyfin.app.requests.dto.GameRequestEvent
import org.gameyfin.app.requests.extensions.toDto

class GameRequestEntityListener {
    @PostPersist
    fun created(gameRequest: GameRequest) {
        GameRequestService.emit(GameRequestEvent.Created(gameRequest.toDto()))
    }

    @PostUpdate
    fun updated(gameRequest: GameRequest) {
        GameRequestService.emit(GameRequestEvent.Updated(gameRequest.toDto()))
    }

    @PostRemove
    fun deleted(gameRequest: GameRequest) {
        GameRequestService.emit(GameRequestEvent.Deleted(gameRequest.id!!))
    }
}