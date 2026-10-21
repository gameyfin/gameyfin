package org.gameyfin.app.collections.dto

import org.gameyfin.app.core.sync.EntityEventType

/**
 * Real-time update for a single collection.
 * - [EntityEventType.CREATED]: [collection] is set
 * - [EntityEventType.UPDATED]: [patch] is set and contains only the changed fields
 * - [EntityEventType.DELETED]: only [id] is set
 */
data class CollectionEvent(
    val type: EntityEventType,
    val id: Long,
    val collection: CollectionDto? = null,
    val patch: CollectionPatchDto? = null
)
