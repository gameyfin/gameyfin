package org.gameyfin.app.games.dto

import com.fasterxml.jackson.annotation.JsonInclude
import org.gameyfin.pluginapi.gamemetadata.*
import java.time.Instant
import java.time.LocalDate

sealed interface GameDto {
    val id: Long
    val createdAt: Instant
    val updatedAt: Instant
    val libraryId: Long
    val collectionIds: List<Long>
    val title: String
    val platforms: List<Platform>
    val coverId: Long?
    val headerId: Long?
    val comment: String?
    val summary: String?
    val release: LocalDate?
    val userRating: Int?
    val criticRating: Int?
    val publishers: List<String>?
    val developers: List<String>?
    val genres: List<Genre>?
    val themes: List<Theme>?
    val keywords: List<String>?
    val features: List<GameFeature>?
    val perspectives: List<PlayerPerspective>?
    val imageIds: List<Long>?
    val videoUrls: List<String>?
    val metadata: GameMetadataDto
}

@JsonInclude(JsonInclude.Include.ALWAYS)
data class GameUserDto(
    override val id: Long,
    override val createdAt: Instant,
    override val updatedAt: Instant,
    override val libraryId: Long,
    override val collectionIds: List<Long>,
    override val title: String,
    override val platforms: List<Platform>,
    override val coverId: Long?,
    override val headerId: Long?,
    override val comment: String?,
    override val summary: String?,
    override val release: LocalDate?,
    override val userRating: Int?,
    override val criticRating: Int?,
    override val publishers: List<String>?,
    override val developers: List<String>?,
    override val genres: List<Genre>?,
    override val themes: List<Theme>?,
    override val keywords: List<String>?,
    override val features: List<GameFeature>?,
    override val perspectives: List<PlayerPerspective>?,
    override val imageIds: List<Long>?,
    override val videoUrls: List<String>?,
    override val metadata: GameMetadataUserDto
) : GameDto

@JsonInclude(JsonInclude.Include.ALWAYS)
data class GameAdminDto(
    override val id: Long,
    override val createdAt: Instant,
    override val updatedAt: Instant,
    override val libraryId: Long,
    override val collectionIds: List<Long>,
    override val title: String,
    override val platforms: List<Platform>,
    override val coverId: Long?,
    override val headerId: Long?,
    override val comment: String?,
    override val summary: String?,
    override val release: LocalDate?,
    override val userRating: Int?,
    override val criticRating: Int?,
    override val publishers: List<String>?,
    override val developers: List<String>?,
    override val genres: List<Genre>?,
    override val themes: List<Theme>?,
    override val keywords: List<String>?,
    override val features: List<GameFeature>?,
    override val perspectives: List<PlayerPerspective>?,
    override val imageIds: List<Long>?,
    override val videoUrls: List<String>?,
    override val metadata: GameMetadataAdminDto
) : GameDto
