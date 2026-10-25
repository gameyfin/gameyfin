package org.gameyfin.app.games.extensions

import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.games.dto.*
import org.gameyfin.app.games.entities.*
import org.gameyfin.app.media.toDto
import java.time.ZoneOffset


fun Game.toDto(): GameDto {
    return if (isCurrentUserAdmin()) {
        this.toAdminDto()
    } else {
        this.toUserDto()
    }
}

fun Collection<Game>.toDtos(): List<GameDto> {
    return if (isCurrentUserAdmin()) {
        this.map { it.toAdminDto() }
    } else {
        this.map { it.toUserDto() }
    }
}

fun Game.toAdminDto(): GameAdminDto {
    return GameAdminDto(
        id = id!!,
        createdAt = createdAt!!,
        updatedAt = updatedAt!!,
        libraryId = this.library.id!!,
        title = title!!,
        platforms = this.platforms,
        cover = this.coverImage?.toDto(),
        header = this.headerImage?.toDto(),
        comment = this.comment,
        summary = this.summary,
        release = this.release?.atZone(ZoneOffset.UTC)?.toLocalDate(),
        userRating = this.userRating,
        criticRating = this.criticRating,
        publishers = this.publishers.map { it.name },
        developers = this.developers.map { it.name },
        genres = this.genres,
        themes = this.themes,
        keywords = this.keywords.toList(),
        features = this.features,
        perspectives = this.perspectives,
        images = this.images.map { it.toDto() },
        videoUrls = this.videoUrls.map { it.toString() },
        metadata = this.metadata.toAdminDto(),
        mediaOrder = this.mediaOrder.toList()
    )
}

fun Game.toUserDto(): GameUserDto {
    return GameUserDto(
        id = id!!,
        createdAt = createdAt!!,
        updatedAt = updatedAt!!,
        libraryId = this.library.id!!,
        title = title!!,
        platforms = this.platforms,
        cover = this.coverImage?.toDto(),
        header = this.headerImage?.toDto(),
        comment = this.comment,
        summary = this.summary,
        release = this.release?.atZone(ZoneOffset.UTC)?.toLocalDate(),
        userRating = this.userRating,
        criticRating = this.criticRating,
        publishers = this.publishers.map { it.name },
        developers = this.developers.map { it.name },
        genres = this.genres,
        themes = this.themes,
        keywords = this.keywords.toList(),
        features = this.features,
        perspectives = this.perspectives,
        images = this.images.map { it.toDto() },
        videoUrls = this.videoUrls.map { it.toString() },
        metadata = this.metadata.toUserDto(),
        mediaOrder = this.mediaOrder.toList()
    )
}

fun Game.toSummaryDto(admin: Boolean): GameSummaryDto {
    return GameSummaryDto(
        id = id!!,
        createdAt = createdAt!!,
        updatedAt = updatedAt!!,
        libraryId = this.library.id!!,
        title = title!!,
        platforms = this.platforms.toList(),
        cover = this.coverImage?.toDto(),
        header = this.headerImage?.toDto(),
        previewImage = this.images.firstOrNull()?.toDto(),
        release = this.release?.atZone(ZoneOffset.UTC)?.toLocalDate(),
        userRating = this.userRating,
        criticRating = this.criticRating,
        publishers = this.publishers.map { it.name }.ifEmpty { null },
        developers = this.developers.map { it.name }.ifEmpty { null },
        genres = this.genres.toList().ifEmpty { null },
        themes = this.themes.toList().ifEmpty { null },
        keywords = this.keywords.toList().ifEmpty { null },
        features = this.features.toList().ifEmpty { null },
        perspectives = this.perspectives.toList().ifEmpty { null },
        fileSize = this.metadata.fileSize ?: 0L,
        path = if (admin) this.metadata.path else null,
        downloadCount = if (admin) this.metadata.downloadCount else null,
        matchConfirmed = if (admin) this.metadata.matchConfirmed else null
    )
}

/**
 * Builds a patch containing only the given fields. Admin-only fields are dropped for non-admins.
 * Returns `null` if no field is visible for the given audience.
 */
fun Game.toPatchDto(fields: Set<GameField>, admin: Boolean): GamePatchDto? {
    val visibleFields = fields.filter { admin || !it.adminOnly }.sorted()
    if (visibleFields.isEmpty()) return null

    var values = GamePatchValuesDto()
    for (field in visibleFields) {
        values = when (field) {
            GameField.LIBRARY_ID -> values.copy(libraryId = library.id)
            GameField.TITLE -> values.copy(title = title)
            GameField.PLATFORMS -> values.copy(platforms = platforms.toList())
            GameField.COVER -> values.copy(cover = coverImage?.toDto())
            GameField.HEADER -> values.copy(header = headerImage?.toDto())
            GameField.COMMENT -> values.copy(comment = comment)
            GameField.SUMMARY -> values.copy(summary = summary)
            GameField.RELEASE -> values.copy(release = release?.atZone(ZoneOffset.UTC)?.toLocalDate())
            GameField.USER_RATING -> values.copy(userRating = userRating)
            GameField.CRITIC_RATING -> values.copy(criticRating = criticRating)
            GameField.PUBLISHERS -> values.copy(publishers = publishers.map { it.name })
            GameField.DEVELOPERS -> values.copy(developers = developers.map { it.name })
            GameField.GENRES -> values.copy(genres = genres.toList())
            GameField.THEMES -> values.copy(themes = themes.toList())
            GameField.KEYWORDS -> values.copy(keywords = keywords.toList())
            GameField.FEATURES -> values.copy(features = features.toList())
            GameField.PERSPECTIVES -> values.copy(perspectives = perspectives.toList())
            GameField.IMAGES -> values.copy(
                images = images.map { it.toDto() },
                previewImage = images.firstOrNull()?.toDto()
            )

            GameField.VIDEO_URLS -> values.copy(videoUrls = videoUrls.map { it.toString() })
            GameField.MEDIA_ORDER -> values.copy(mediaOrder = mediaOrder.toList())
            GameField.FILE_SIZE -> values.copy(fileSize = metadata.fileSize ?: 0L)
            GameField.PATH -> values.copy(path = metadata.path)
            GameField.DOWNLOAD_COUNT -> values.copy(downloadCount = metadata.downloadCount)
            GameField.MATCH_CONFIRMED -> values.copy(matchConfirmed = metadata.matchConfirmed)
            GameField.FIELD_METADATA -> values.copy(fieldMetadata = metadata.fields.mapValues { it.value.toDto() })
            GameField.ORIGINAL_IDS -> values.copy(originalIds = metadata.originalIds.mapKeys { it.key.pluginId })
        }
    }

    return GamePatchDto(updatedAt = updatedAt!!, fields = visibleFields, values = values)
}

fun GameMetadata.toAdminDto(): GameMetadataAdminDto {
    return GameMetadataAdminDto(
        fileSize = this.fileSize ?: 0L,
        downloadCount = this.downloadCount,
        path = this.path,
        fields = this.fields.mapValues { it.value.toDto() },
        originalIds = this.originalIds.mapKeys { it.key.pluginId },
        matchConfirmed = this.matchConfirmed
    )
}

fun GameMetadata.toUserDto(): GameMetadataUserDto {
    return GameMetadataUserDto(
        fileSize = this.fileSize ?: 0L
    )
}

private fun GameFieldMetadata.toDto(): GameFieldMetadataDto {
    return when (val source = this.source) {
        is GameFieldPluginSource -> {
            GameFieldMetadataDto(
                type = GameFieldMetadataType.PLUGIN,
                source = source.plugin.pluginId,
                updatedAt = this.updatedAt!!
            )
        }

        is GameFieldUserSource -> {
            GameFieldMetadataDto(
                type = GameFieldMetadataType.USER,
                source = source.user.username,
                updatedAt = this.updatedAt!!
            )
        }

        else -> {
            GameFieldMetadataDto(
                type = GameFieldMetadataType.UNKNOWN,
                source = "unknown source",
                updatedAt = this.updatedAt!!
            )
        }
    }
}
