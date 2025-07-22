package org.gameyfin.app.games.entities

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.dto.GameAdminEvent
import org.gameyfin.app.games.dto.GameUserEvent
import org.gameyfin.app.games.extensions.toAdminDto
import org.gameyfin.app.games.extensions.toUserDto

class GameEntityListener {
    @PostPersist
    fun created(game: Game) {
        GameService.Companion.emitUser(GameUserEvent.Created(game.toUserDto()))
        GameService.Companion.emitAdmin(GameAdminEvent.Created(game.toAdminDto()))
    }

    @PostUpdate
    fun updated(game: Game) {
        GameService.Companion.emitUser(GameUserEvent.Updated(game.toUserDto()))
        GameService.Companion.emitAdmin(GameAdminEvent.Updated(game.toAdminDto()))
    }

    @PostRemove
    fun deleted(game: Game) {
        GameService.Companion.emitUser(GameUserEvent.Deleted(game.id!!))
        GameService.Companion.emitAdmin(GameAdminEvent.Deleted(game.id!!))
    }
}