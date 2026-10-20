package org.gameyfin.app.libraries.entities

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import org.gameyfin.app.core.events.LibraryCreatedEvent
import org.gameyfin.app.core.events.LibraryDeletedEvent
import org.gameyfin.app.core.events.LibraryUpdatedEvent
import org.gameyfin.app.util.EventPublisherHolder

class LibraryEntityListener {
    @PostPersist
    fun created(library: Library) {
        EventPublisherHolder.publish(LibraryCreatedEvent(this, library))
    }

    @PostUpdate
    fun updated(library: Library) {
        EventPublisherHolder.publish(LibraryUpdatedEvent(this, library))
    }

    @PostRemove
    fun deleted(library: Library) {
        EventPublisherHolder.publish(LibraryDeletedEvent(this, library))
    }
}