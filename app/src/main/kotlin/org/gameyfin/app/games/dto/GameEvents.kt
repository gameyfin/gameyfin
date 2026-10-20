package org.gameyfin.app.games.dto

import org.gameyfin.app.core.sync.EntityEventType

/**
 * Real-time update for a single game.
 * - [EntityEventType.CREATED]: [summary] is set
 * - [EntityEventType.UPDATED]: [patch] is set and contains only the changed fields
 * - [EntityEventType.DELETED]: only [id] is set
 */
data class GameEvent(
    val type: EntityEventType,
    val id: Long,
    val summary: GameSummaryDto? = null,
    val patch: GamePatchDto? = null
)
