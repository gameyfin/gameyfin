package org.gameyfin.app.games.dto

import java.time.Instant

class GameFieldMetadataDto(
    val type: GameFieldMetadataType,
    val source: String,
    val updatedAt: Instant,
    val sourceName: String? = null
)

enum class GameFieldMetadataType {
    PLUGIN,
    USER,
    API_KEY,
    UNKNOWN
}
