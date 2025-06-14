package org.gameyfin.app.core.filesystem

data class FileDto(
    val name: String,
    val type: FileType,
    val hash: Int
)