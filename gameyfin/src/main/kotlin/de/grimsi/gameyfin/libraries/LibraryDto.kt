package de.grimsi.gameyfin.libraries

data class LibraryDto(
    val id: Long,
    val name: String,
    val path: String,
    val stats: LibraryStatsDto?
)