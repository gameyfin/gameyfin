package org.gameyfin.app.collections.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

/**
 * Representation of a collection. Collections are small, so this is used both for overview pages and details.
 * Statistics (game count, downloads, platforms) are derived on the client from the game summaries.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CollectionDto(
    val id: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val name: String,
    val description: String?,
    val gameIds: List<Long> = emptyList(),
    val metadata: CollectionMetadataDto,
)

data class CollectionSyncDto(
    /** Server timestamp to be used as `since` parameter for the next delta sync */
    val serverTime: Instant,
    /** If `true`, [collections] contains all collections and the client should discard everything else */
    val full: Boolean,
    val collections: List<CollectionDto>,
    /** IDs of all existing collections (only set for delta syncs), used by the client to detect deletions */
    val allIds: List<Long>
)

enum class CollectionField {
    NAME,
    DESCRIPTION,
    GAME_IDS,
    METADATA
}

/**
 * Describes a partial update of a collection. Only the values listed in [fields] are valid, all other values must
 * be ignored by the client (a listed field with a `null`/missing value has been cleared).
 */
data class CollectionPatchDto(
    val updatedAt: Instant,
    val fields: List<CollectionField>,
    val values: CollectionPatchValuesDto
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CollectionPatchValuesDto(
    val name: String? = null,
    val description: String? = null,
    val gameIds: List<Long>? = null,
    val metadata: CollectionMetadataDto? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CollectionCreateDto(
    val name: String,
    val description: String? = null,
    val gameIds: List<Long>? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CollectionUpdateDto(
    val id: Long,
    val name: String? = null,
    val description: String? = null,
    val gameIds: List<Long>? = null,
    val metadata: CollectionMetadataUpdateDto? = null
)

