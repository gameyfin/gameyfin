package org.gameyfin.app.games.dto

import java.time.LocalDate

data class GameUpdateDto(
    val id: Long,
    val title: String?,
    val release: LocalDate?,
    val coverUrl: String?,
    val headerUrl: String?,
    val comment: String?,
    val summary: String?,
    val developers: List<String>?,
    val publishers: List<String>?,
    val genres: List<String>?,
    val themes: List<String>?,
    val keywords: List<String>?,
    val features: List<String>?,
    val perspectives: List<String>?,
    val metadata: GameUpdateMetadataDto?
)