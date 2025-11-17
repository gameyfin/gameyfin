package org.gameyfin.app.core.filesystem

import org.gameyfin.app.libraries.entities.IgnoredPath
import java.nio.file.Path

data class FilesystemScanResult(
    val newPaths: List<Path>,
    val removedGamePaths: List<Path>,
    val removedIgnoredPaths: List<IgnoredPath>
)
