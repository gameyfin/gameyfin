package de.grimsi.gameyfin.games.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
class GameDto(
    val id: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val libraryId: Long,
    val title: String,
    val coverId: Long?,
    val comment: String?,
    val summary: String?,
    val release: LocalDate?,
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
    val metadata: GameMetadataDto
)