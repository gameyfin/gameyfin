package de.grimsi.gameyfin.core.filesystem

import java.nio.file.Path

data class FilesystemScanResult(
    val newPaths: Set<Path>,
    val removedGamePaths: Set<Path>,
    val removedUnmatchedPaths: Set<Path>
)
