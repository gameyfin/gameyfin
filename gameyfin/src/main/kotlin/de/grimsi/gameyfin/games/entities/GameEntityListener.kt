package de.grimsi.gameyfin.games.entities

import de.grimsi.gameyfin.games.GameService
import de.grimsi.gameyfin.games.dto.GameEvent
import de.grimsi.gameyfin.games.toDto
import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate

class GameEntityListener {
    @PostPersist
    fun created(game: Game) {
        val event = GameEvent.Created(game.toDto())
        GameService.emit(event)
    }

    @PostUpdate
    fun updated(game: Game) {
        val event = GameEvent.Updated(game.toDto())
        GameService.emit(event)
    }

    @PostRemove
    fun deleted(game: Game) {
        val event = GameEvent.Deleted(game.id!!)
        GameService.emit(event)
    }
}