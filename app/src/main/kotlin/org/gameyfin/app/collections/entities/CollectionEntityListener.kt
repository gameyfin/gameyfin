package org.gameyfin.app.collections.entities

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import org.gameyfin.app.core.events.CollectionCreatedEvent
import org.gameyfin.app.core.events.CollectionDeletedEvent
import org.gameyfin.app.core.events.CollectionUpdatedEvent
import org.gameyfin.app.util.EventPublisherHolder

class CollectionEntityListener {
    @PostPersist
    fun created(collection: Collection) {
        EventPublisherHolder.publish(CollectionCreatedEvent(this, collection))
    }

    @PostUpdate
    fun updated(collection: Collection) {
        EventPublisherHolder.publish(CollectionUpdatedEvent(this, collection))
    }

    @PostRemove
    fun deleted(collection: Collection) {
        EventPublisherHolder.publish(CollectionDeletedEvent(this, collection))
    }
}
