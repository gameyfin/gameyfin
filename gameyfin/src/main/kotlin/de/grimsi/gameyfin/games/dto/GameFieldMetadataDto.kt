package de.grimsi.gameyfin.games.dto

import java.io.Serializable
import java.time.Instant

class GameFieldMetadataDto(
    val type: GameFieldMetadataType,
    val source: Serializable,
    val updatedAt: Instant
)

enum class GameFieldMetadataType {
    PLUGIN,
    USER,
    UNKNOWN
}