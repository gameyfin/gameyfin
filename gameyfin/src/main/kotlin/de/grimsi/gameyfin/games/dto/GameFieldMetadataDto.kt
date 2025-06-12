package de.grimsi.gameyfin.games.dto

import java.time.Instant

class GameFieldMetadataDto(
    val type: GameFieldMetadataType,
    val source: String,
    val updatedAt: Instant
)

enum class GameFieldMetadataType {
    PLUGIN,
    USER,
    UNKNOWN
}