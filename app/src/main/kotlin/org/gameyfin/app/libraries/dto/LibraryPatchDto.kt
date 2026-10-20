package org.gameyfin.app.libraries.dto

import com.fasterxml.jackson.annotation.JsonInclude
import org.gameyfin.pluginapi.gamemetadata.Platform
import java.time.Instant

enum class LibraryField(val adminOnly: Boolean = false) {
    NAME,
    METADATA,
    PLATFORMS(adminOnly = true),
    DIRECTORIES(adminOnly = true),
    IGNORED_PATHS(adminOnly = true)
}

/**
 * Describes a partial update of a library. Only the values listed in [fields] are valid, all other values must be
 * ignored by the client (a listed field with a `null`/missing value has been cleared).
 */
data class LibraryPatchDto(
    val updatedAt: Instant,
    val fields: List<LibraryField>,
    val values: LibraryPatchValuesDto
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class LibraryPatchValuesDto(
    val name: String? = null,
    val metadata: LibraryMetadataDto? = null,
    val platforms: List<Platform>? = null,
    val directories: List<DirectoryMappingDto>? = null,
    val ignoredPaths: List<IgnoredPathDto>? = null
)
