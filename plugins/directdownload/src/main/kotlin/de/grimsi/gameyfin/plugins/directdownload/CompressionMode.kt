package de.grimsi.gameyfin.plugins.directdownload

import de.grimsi.gameyfin.plugins.directdownload.CompressionMode.*
import java.util.zip.Deflater

enum class CompressionMode {
    NONE,
    FAST,
    BEST;
}

fun CompressionMode.deflaterLevel(): Int {
    return when (this) {
        NONE -> Deflater.NO_COMPRESSION
        FAST -> Deflater.BEST_SPEED
        BEST -> Deflater.BEST_COMPRESSION
    }
}