package org.gameyfin.app.collections.entities

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import org.gameyfin.app.collections.CollectionService
import org.gameyfin.app.collections.dto.CollectionAdminEvent
import org.gameyfin.app.collections.dto.CollectionUserEvent
import org.gameyfin.app.collections.extensions.toAdminDto
import org.gameyfin.app.collections.extensions.toUserDto
import org.gameyfin.app.core.events.CollectionCreatedEvent
import org.gameyfin.app.core.events.CollectionDeletedEvent
import org.gameyfin.app.core.events.CollectionUpdatedEvent
import org.gameyfin.app.util.EventPublisherHolder

class CollectionEntityListener {
    @PostPersist
    fun created(collection: Collection) {
        CollectionService.emitUser(CollectionUserEvent.Created(collection.toUserDto()))
        CollectionService.emitAdmin(CollectionAdminEvent.Created(collection.toAdminDto()))
        EventPublisherHolder.publish(CollectionCreatedEvent(this, collection))
    }

    @PostUpdate
    fun updated(collection: Collection) {
        CollectionService.emitUser(CollectionUserEvent.Updated(collection.toUserDto()))
        CollectionService.emitAdmin(CollectionAdminEvent.Updated(collection.toAdminDto()))
        EventPublisherHolder.publish(CollectionUpdatedEvent(this, collection))
    }

    @PostRemove
    fun deleted(collection: Collection) {
        CollectionService.emitUser(CollectionUserEvent.Deleted(collection.id!!))
        CollectionService.emitAdmin(CollectionAdminEvent.Deleted(collection.id!!))
        EventPublisherHolder.publish(CollectionDeletedEvent(this, collection))
    }
}
