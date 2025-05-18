package de.grimsi.gameyfin.core.filesystem

import java.nio.file.Path

data class FilesystemScanResult(
    val newPaths: List<Path>,
    val removedGamePaths: List<Path>,
    val removedUnmatchedPaths: List<Path>
)
