package org.gameyfin.app.games.entities

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PreRemove
import org.gameyfin.app.core.events.GameCreatedEvent
import org.gameyfin.app.core.events.GameDeletedEvent
import org.gameyfin.app.util.EventPublisherHolder
import org.hibernate.Hibernate

/**
 * Publishes application events for game lifecycle changes.
 * Real-time updates to clients are handled by [org.gameyfin.app.core.sync.EntityChangeTracker].
 */
class GameEntityListener {

    @PostPersist
    fun created(game: Game) {
        EventPublisherHolder.publish(GameCreatedEvent(this, game))
    }

    // GameUpdatedEvent is triggered via {@link org.gameyfin.app.core.interceptors.EntityUpdateInterceptor#onFlushDirty}

    @PreRemove
    fun preDelete(game: Game) {
        // Initialize lazy images so they are still accessible to asynchronous listeners of the GameDeletedEvent
        Hibernate.initialize(game.images)
        val collectionsToUpdate = game.collections.toList()
        collectionsToUpdate.forEach { it.removeGame(game) }
        game.collections.clear()
    }

    @PostRemove
    fun deleted(game: Game) {
        EventPublisherHolder.publish(GameDeletedEvent(this, game))
    }
}
