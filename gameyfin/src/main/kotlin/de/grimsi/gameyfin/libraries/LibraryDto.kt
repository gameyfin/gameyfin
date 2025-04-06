package de.grimsi.gameyfin.libraries

data class LibraryDto(
    val id: Long,
    val name: String,
    val directories: Set<String>,
    val stats: LibraryStatsDto?
)