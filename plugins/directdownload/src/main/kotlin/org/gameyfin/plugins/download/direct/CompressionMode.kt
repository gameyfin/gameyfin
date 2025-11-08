package org.gameyfin.plugins.download.direct

import org.gameyfin.plugins.download.direct.CompressionMode.*
import java.util.zip.Deflater

enum class CompressionMode {
    None,
    Fast,
    Best;
}

fun CompressionMode.deflaterLevel(): Int {
    return when (this) {
        None -> Deflater.NO_COMPRESSION
        Fast -> Deflater.BEST_SPEED
        Best -> Deflater.BEST_COMPRESSION
    }
}