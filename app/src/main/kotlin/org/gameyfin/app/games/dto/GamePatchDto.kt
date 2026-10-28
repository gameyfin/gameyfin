package org.gameyfin.app.games.dto

import com.fasterxml.jackson.annotation.JsonInclude
import org.gameyfin.app.media.ImageDto
import org.gameyfin.pluginapi.gamemetadata.*
import java.time.Instant
import java.time.LocalDate

enum class GameField(val adminOnly: Boolean = false) {
    LIBRARY_ID,
    TITLE,
    PLATFORMS,
    COVER,
    HEADER,
    COMMENT,
    SUMMARY,
    RELEASE,
    USER_RATING,
    CRITIC_RATING,
    PUBLISHERS,
    DEVELOPERS,
    GENRES,
    THEMES,
    KEYWORDS,
    FEATURES,
    PERSPECTIVES,

    /** Also updates `previewImage` of the summary */
    IMAGES,
    VIDEO_URLS,
    MEDIA_ORDER,
    FILE_SIZE,
    PATH(adminOnly = true),
    DOWNLOAD_COUNT(adminOnly = true),
    MATCH_CONFIRMED(adminOnly = true),
    FIELD_METADATA(adminOnly = true),
    ORIGINAL_IDS(adminOnly = true)
}

/**
 * Describes a partial update of a game. Only the values listed in [fields] are valid, all other values must be
 * ignored by the client (a listed field with a `null`/missing value has been cleared).
 */
data class GamePatchDto(
    val updatedAt: Instant,
    val fields: List<GameField>,
    val values: GamePatchValuesDto
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GamePatchValuesDto(
    val libraryId: Long? = null,
    val title: String? = null,
    val platforms: List<Platform>? = null,
    val cover: ImageDto? = null,
    val header: ImageDto? = null,
    val comment: String? = null,
    val summary: String? = null,
    val release: LocalDate? = null,
    val userRating: Int? = null,
    val criticRating: Int? = null,
    val publishers: List<String>? = null,
    val developers: List<String>? = null,
    val genres: List<Genre>? = null,
    val themes: List<Theme>? = null,
    val keywords: List<String>? = null,
    val features: List<GameFeature>? = null,
    val perspectives: List<PlayerPerspective>? = null,
    val images: List<ImageDto>? = null,
    val previewImage: ImageDto? = null,
    val videoUrls: List<String>? = null,
    val mediaOrder: List<String>? = null,
    val fileSize: Long? = null,
    val path: String? = null,
    val downloadCount: Int? = null,
    val matchConfirmed: Boolean? = null,
    val fieldMetadata: Map<String, GameFieldMetadataDto>? = null,
    val originalIds: Map<String, String>? = null
)
