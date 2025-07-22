package org.gameyfin.app.games.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.time.LocalDate

sealed interface GameDto {
    val id: Long
    val createdAt: Instant
    val updatedAt: Instant
    val libraryId: Long
    val title: String
    val coverId: Long?
    val headerId: Long?
    val comment: String?
    val summary: String?
    val release: LocalDate?
    val userRating: Int?
    val criticRating: Int?
    val publishers: List<String>?
    val developers: List<String>?
    val genres: List<String>?
    val themes: List<String>?
    val keywords: List<String>?
    val features: List<String>?
    val perspectives: List<String>?
    val imageIds: List<Long>?
    val videoUrls: List<String>?
    val metadata: GameMetadataDto
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GameUserDto(
    override val id: Long,
    override val createdAt: Instant,
    override val updatedAt: Instant,
    override val libraryId: Long,
    override val title: String,
    override val coverId: Long?,
    override val headerId: Long?,
    override val comment: String?,
    override val summary: String?,
    override val release: LocalDate?,
    override val userRating: Int?,
    override val criticRating: Int?,
    override val publishers: List<String>?,
    override val developers: List<String>?,
    override val genres: List<String>?,
    override val themes: List<String>?,
    override val keywords: List<String>?,
    override val features: List<String>?,
    override val perspectives: List<String>?,
    override val imageIds: List<Long>?,
    override val videoUrls: List<String>?,
    override val metadata: GameMetadataUserDto
) : GameDto

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GameAdminDto(
    override val id: Long,
    override val createdAt: Instant,
    override val updatedAt: Instant,
    override val libraryId: Long,
    override val title: String,
    override val coverId: Long?,
    override val headerId: Long?,
    override val comment: String?,
    override val summary: String?,
    override val release: LocalDate?,
    override val userRating: Int?,
    override val criticRating: Int?,
    override val publishers: List<String>?,
    override val developers: List<String>?,
    override val genres: List<String>?,
    override val themes: List<String>?,
    override val keywords: List<String>?,
    override val features: List<String>?,
    override val perspectives: List<String>?,
    override val imageIds: List<Long>?,
    override val videoUrls: List<String>?,
    override val metadata: GameMetadataAdminDto
) : GameDto
