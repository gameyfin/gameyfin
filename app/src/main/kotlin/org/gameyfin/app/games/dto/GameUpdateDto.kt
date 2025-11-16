package org.gameyfin.app.games.dto

import org.gameyfin.pluginapi.gamemetadata.*
import java.time.LocalDate

data class GameUpdateDto(
    val id: Long,
    val title: String?,
    val platforms: Set<Platform>?,
    val release: LocalDate?,
    val coverUrl: String?,
    val headerUrl: String?,
    val comment: String?,
    val summary: String?,
    val developers: List<String>?,
    val publishers: List<String>?,
    val genres: List<Genre>?,
    val themes: List<Theme>?,
    val features: List<GameFeature>?,
    val perspectives: List<PlayerPerspective>?,
    val keywords: List<String>?,
    val metadata: GameUpdateMetadataDto?
)