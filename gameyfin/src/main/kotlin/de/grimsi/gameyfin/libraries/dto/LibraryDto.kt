package de.grimsi.gameyfin.libraries.dto

data class LibraryDto(
    val id: Long,
    val name: String,
    val directories: List<DirectoryMappingDto>,
    val stats: LibraryStatsDto?
)