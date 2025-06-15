package org.gameyfin.app.libraries.dto

data class DirectoryMappingDto(
    val internalPath: String,
    val externalPath: String? = null,
)
