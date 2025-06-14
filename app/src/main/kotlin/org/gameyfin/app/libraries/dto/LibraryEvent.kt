package org.gameyfin.app.libraries.dto

sealed class LibraryEvent {
    abstract val type: String

    data class Created(val library: LibraryDto, override val type: String = "created") : LibraryEvent()
    data class Updated(val library: LibraryDto, override val type: String = "updated") : LibraryEvent()
    data class Deleted(val libraryId: Long, override val type: String = "deleted") : LibraryEvent()
}