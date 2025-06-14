package org.gameyfin.app.libraries.dto

data class LibraryUpdateDto(
    val id: Long,
    val name: String? = null,
    val directories: List<DirectoryMappingDto>? = null,
    val unmatchedPaths: List<String>? = null
)
