package de.grimsi.gameyfin.games.dto

import java.time.Instant

class GameDto(
    val id: Long,
    val title: String,
    val coverId: Long?,
    val comment: String?,
    val summary: String?,
    val release: Instant?,
    val userRating: Int?,
    val criticRating: Int?,
    val publishers: List<String>?,
    val developers: List<String>?,
    val genres: List<String>?,
    val themes: List<String>?,
    val keywords: List<String>?,
    val features: List<String>?,
    val perspectives: List<String>?,
    val imageIds: List<Long>?,
    val videoUrls: List<String>?,
    val path: String,
    val metadata: Map<String, GameMetadataDto>,
    val originalIds: Map<String, String>
)