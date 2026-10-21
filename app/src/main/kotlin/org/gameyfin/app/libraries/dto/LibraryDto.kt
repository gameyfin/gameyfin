package org.gameyfin.app.libraries.dto

import com.fasterxml.jackson.annotation.JsonInclude
import org.gameyfin.pluginapi.gamemetadata.Platform
import java.time.Instant

/**
 * Lightweight representation of a library used by all overview pages.
 * Game IDs and statistics are derived on the client from the game summaries.
 * Admin-only fields are `null` (and therefore omitted from the JSON) for non-admin users.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LibrarySummaryDto(
    val id: Long,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val metadata: LibraryMetadataDto,
    /* Admin only */
    val platforms: List<Platform>? = null
)

/**
 * Full representation of a library (admin only), fetched on demand. Also used as input to create libraries.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LibraryAdminDto(
    val id: Long,
    val name: String,
    val createdAt: Instant?,
    val updatedAt: Instant? = null,
    val directories: List<DirectoryMappingDto>,
    val platforms: List<Platform>,
    val ignoredPaths: List<IgnoredPathDto>?,
    val metadata: LibraryMetadataDto?
)

data class LibrarySyncDto(
    /** Server timestamp to be used as `since` parameter for the next delta sync */
    val serverTime: Instant,
    /** If `true`, [libraries] contains all libraries and the client should discard everything else */
    val full: Boolean,
    val libraries: List<LibrarySummaryDto>,
    /** IDs of all existing libraries (only set for delta syncs), used by the client to detect deletions */
    val allIds: List<Long>
)
