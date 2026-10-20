package org.gameyfin.app.games.dto

import com.fasterxml.jackson.annotation.JsonInclude
import org.gameyfin.app.media.ImageDto
import org.gameyfin.pluginapi.gamemetadata.*
import java.time.Instant
import java.time.LocalDate

/**
 * Lightweight representation of a game containing only the fields required by overview pages, search and filters.
 * The full representation ([GameDto]) is fetched on demand.
 *
 * Admin-only fields are `null` (and therefore omitted from the JSON) for non-admin users.
 * Empty lists are serialized as `null` to reduce the payload size.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GameSummaryDto(
    val id: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val libraryId: Long,
    val title: String,
    val platforms: List<Platform>,
    val cover: ImageDto?,
    val header: ImageDto?,
    val previewImage: ImageDto?,
    val release: LocalDate?,
    val userRating: Int?,
    val criticRating: Int?,
    val publishers: List<String>?,
    val developers: List<String>?,
    val genres: List<Genre>?,
    val themes: List<Theme>?,
    val keywords: List<String>?,
    val features: List<GameFeature>?,
    val perspectives: List<PlayerPerspective>?,
    val fileSize: Long,
    /* Admin only */
    val path: String? = null,
    val downloadCount: Int? = null,
    val matchConfirmed: Boolean? = null
)

data class GameSyncDto(
    /** Server timestamp to be used as `since` parameter for the next delta sync */
    val serverTime: Instant,
    /** If `true`, [games] contains all games and the client should discard everything else */
    val full: Boolean,
    val games: List<GameSummaryDto>,
    /** IDs of all existing games (only set for delta syncs), used by the client to detect deletions */
    val allIds: List<Long>
)
