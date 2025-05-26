package de.grimsi.gameyfin.games.entities

import de.grimsi.gameyfin.libraries.Library
import de.grimsi.gameyfin.libraries.LibraryService
import de.grimsi.gameyfin.libraries.dto.LibraryEvent
import de.grimsi.gameyfin.libraries.toDto
import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate

class LibraryEntityListener {
    @PostPersist
    fun created(library: Library) {
        val event = LibraryEvent.Created(library.toDto())
        LibraryService.emit(event)
    }

    @PostUpdate
    fun updated(library: Library) {
        val event = LibraryEvent.Updated(library.toDto())
        LibraryService.emit(event)
    }

    @PostRemove
    fun deleted(library: Library) {
        val event = LibraryEvent.Deleted(library.id!!)
        LibraryService.emit(event)
    }
}