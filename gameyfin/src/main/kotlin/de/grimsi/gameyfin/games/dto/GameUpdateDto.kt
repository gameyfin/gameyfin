package de.grimsi.gameyfin.games.dto

import java.time.LocalDate

data class GameUpdateDto(
    val id: Long,
    val title: String?,
    val release: LocalDate?,
    val coverUrl: String?,
    val comment: String?,
    val summary: String?,
    val metadata: GameUpdateMetadataDto?
)