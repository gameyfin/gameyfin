package org.gameyfin.app.games.entities

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import org.gameyfin.app.libraries.Library
import org.gameyfin.app.libraries.LibraryService
import org.gameyfin.app.libraries.dto.LibraryEvent
import org.gameyfin.app.libraries.extensions.toDto

class LibraryEntityListener {
    @PostPersist
    fun created(library: Library) {
        val event = LibraryEvent.Created(library.toDto())
        LibraryService.Companion.emit(event)
    }

    @PostUpdate
    fun updated(library: Library) {
        val event = LibraryEvent.Updated(library.toDto())
        LibraryService.Companion.emit(event)
    }

    @PostRemove
    fun deleted(library: Library) {
        val event = LibraryEvent.Deleted(library.id!!)
        LibraryService.Companion.emit(event)
    }
}