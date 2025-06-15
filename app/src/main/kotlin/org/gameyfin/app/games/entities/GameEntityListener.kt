package org.gameyfin.app.games.entities

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.dto.GameEvent
import org.gameyfin.app.games.toDto

class GameEntityListener {
    @PostPersist
    fun created(game: Game) {
        val event = GameEvent.Created(game.toDto())
        GameService.Companion.emit(event)
    }

    @PostUpdate
    fun updated(game: Game) {
        val event = GameEvent.Updated(game.toDto())
        GameService.Companion.emit(event)
    }

    @PostRemove
    fun deleted(game: Game) {
        val event = GameEvent.Deleted(game.id!!)
        GameService.Companion.emit(event)
    }
}