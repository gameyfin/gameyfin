package de.grimsi.gameyfin.core.filesystem

data class FileDto(
    val name: String,
    val type: FileType,
    val hash: Int
)