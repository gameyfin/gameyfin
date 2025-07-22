package org.gameyfin.app.libraries.dto

sealed interface LibraryEvent {
    val type: String
}

sealed class LibraryUserEvent : LibraryEvent {
    data class Created(val library: LibraryUserDto, override val type: String = "created") : LibraryUserEvent()
    data class Updated(val library: LibraryUserDto, override val type: String = "updated") : LibraryUserEvent()
    data class Deleted(val libraryId: Long, override val type: String = "deleted") : LibraryUserEvent()
}

sealed class LibraryAdminEvent : LibraryEvent {
    data class Created(val library: LibraryAdminDto, override val type: String = "created") : LibraryAdminEvent()
    data class Updated(val library: LibraryAdminDto, override val type: String = "updated") : LibraryAdminEvent()
    data class Deleted(val libraryId: Long, override val type: String = "deleted") : LibraryAdminEvent()
}