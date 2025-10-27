package org.gameyfin.app.libraries.entities

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import org.gameyfin.app.core.events.LibraryCreatedEvent
import org.gameyfin.app.core.events.LibraryDeletedEvent
import org.gameyfin.app.core.events.LibraryUpdatedEvent
import org.gameyfin.app.libraries.LibraryService
import org.gameyfin.app.libraries.dto.LibraryAdminEvent
import org.gameyfin.app.libraries.dto.LibraryUserEvent
import org.gameyfin.app.libraries.extensions.toAdminDto
import org.gameyfin.app.libraries.extensions.toUserDto
import org.gameyfin.app.util.EventPublisherHolder

class LibraryEntityListener {
    @PostPersist
    fun created(library: Library) {
        LibraryService.emitUser(LibraryUserEvent.Created(library.toUserDto()))
        LibraryService.emitAdmin(LibraryAdminEvent.Created(library.toAdminDto()))
        EventPublisherHolder.publish(LibraryCreatedEvent(this, library))
    }

    @PostUpdate
    fun updated(library: Library) {
        LibraryService.emitUser(LibraryUserEvent.Updated(library.toUserDto()))
        LibraryService.emitAdmin(LibraryAdminEvent.Updated(library.toAdminDto()))
        EventPublisherHolder.publish(LibraryUpdatedEvent(this, library))
    }

    @PostRemove
    fun deleted(library: Library) {
        LibraryService.emitUser(LibraryUserEvent.Deleted(library.id!!))
        LibraryService.emitAdmin(LibraryAdminEvent.Deleted(library.id!!))
        EventPublisherHolder.publish(LibraryDeletedEvent(this, library))
    }
}