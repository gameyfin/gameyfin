package org.gameyfin.app.collections.dto

sealed interface CollectionEvent {
    val type: String
}

sealed class CollectionUserEvent : CollectionEvent {
    data class Created(val collection: CollectionUserDto, override val type: String = "created") : CollectionUserEvent()
    data class Updated(val collection: CollectionUserDto, override val type: String = "updated") : CollectionUserEvent()
    data class Deleted(val collectionId: Long, override val type: String = "deleted") : CollectionUserEvent()
}

sealed class CollectionAdminEvent : CollectionEvent {
    data class Created(val collection: CollectionAdminDto, override val type: String = "created") :
        CollectionAdminEvent()

    data class Updated(val collection: CollectionAdminDto, override val type: String = "updated") :
        CollectionAdminEvent()

    data class Deleted(val collectionId: Long, override val type: String = "deleted") : CollectionAdminEvent()
}

