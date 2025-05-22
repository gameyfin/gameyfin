package de.grimsi.gameyfin.games.dto

data class GameUpdateDto(
    val id: Long,
    val title: String?,
    val comment: String?,
    val summary: String?,
)