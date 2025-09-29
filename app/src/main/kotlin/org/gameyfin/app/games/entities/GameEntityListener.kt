package org.gameyfin.app.games.entities

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import org.gameyfin.app.core.events.GameCreatedEvent
import org.gameyfin.app.core.events.GameDeletedEvent
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.dto.GameAdminEvent
import org.gameyfin.app.games.dto.GameUserEvent
import org.gameyfin.app.games.extensions.toAdminDto
import org.gameyfin.app.games.extensions.toUserDto
import org.gameyfin.app.util.EventPublisherHolder

class GameEntityListener {

    @PostPersist
    fun created(game: Game) {
        GameService.emitUser(GameUserEvent.Created(game.toUserDto()))
        GameService.emitAdmin(GameAdminEvent.Created(game.toAdminDto()))
        EventPublisherHolder.publish(GameCreatedEvent(this, game))
    }

    @PostUpdate
    fun updated(game: Game) {
        GameService.emitUser(GameUserEvent.Updated(game.toUserDto()))
        GameService.emitAdmin(GameAdminEvent.Updated(game.toAdminDto()))
        // GameUpdateEvent triggered via {@link org.gameyfin.app.core.interceptors.EntityUpdateInterceptor#onFlushDirty}
    }

    @PostRemove
    fun deleted(game: Game) {
        GameService.emitUser(GameUserEvent.Deleted(game.id!!))
        GameService.emitAdmin(GameAdminEvent.Deleted(game.id!!))
        EventPublisherHolder.publish(GameDeletedEvent(this, game))
    }
}