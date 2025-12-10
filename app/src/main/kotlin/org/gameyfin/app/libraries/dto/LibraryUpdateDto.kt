package org.gameyfin.app.libraries.dto

import org.gameyfin.pluginapi.gamemetadata.Platform

data class LibraryUpdateDto(
    val id: Long,
    val name: String? = null,
    val directories: List<DirectoryMappingDto>? = null,
    val platforms: List<Platform>? = null,
    val ignoredPaths: List<IgnoredPathDto>? = null,
    val metadata: LibraryMetadataDto? = null
)
