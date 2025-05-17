package de.grimsi.gameyfin.libraries.dto

data class LibraryUpdateDto(
    val id: Long,
    val name: String? = null,
    val directories: Set<DirectoryMappingDto>? = null,
)
