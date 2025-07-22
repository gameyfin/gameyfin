package org.gameyfin.app.games.entities

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import org.gameyfin.app.libraries.Library
import org.gameyfin.app.libraries.LibraryService
import org.gameyfin.app.libraries.dto.LibraryAdminEvent
import org.gameyfin.app.libraries.dto.LibraryUserEvent
import org.gameyfin.app.libraries.extensions.toAdminDto
import org.gameyfin.app.libraries.extensions.toUserDto

class LibraryEntityListener {
    @PostPersist
    fun created(library: Library) {
        LibraryService.emitUser(LibraryUserEvent.Created(library.toUserDto()))
        LibraryService.emitAdmin(LibraryAdminEvent.Created(library.toAdminDto()))
    }

    @PostUpdate
    fun updated(library: Library) {
        LibraryService.emitUser(LibraryUserEvent.Updated(library.toUserDto()))
        LibraryService.emitAdmin(LibraryAdminEvent.Updated(library.toAdminDto()))
    }

    @PostRemove
    fun deleted(library: Library) {
        LibraryService.emitUser(LibraryUserEvent.Deleted(library.id!!))
        LibraryService.emitAdmin(LibraryAdminEvent.Deleted(library.id!!))
    }
}