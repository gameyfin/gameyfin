package org.gameyfin.app.libraries.dto

import org.gameyfin.app.core.sync.EntityEventType

/**
 * Real-time update for a single library.
 * - [EntityEventType.CREATED]: [summary] is set
 * - [EntityEventType.UPDATED]: [patch] is set and contains only the changed fields
 * - [EntityEventType.DELETED]: only [id] is set
 */
data class LibraryEvent(
    val type: EntityEventType,
    val id: Long,
    val summary: LibrarySummaryDto? = null,
    val patch: LibraryPatchDto? = null
)
