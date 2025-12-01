package org.gameyfin.app.games.extensions

import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.games.dto.*
import org.gameyfin.app.games.entities.*
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
        collectionIds = this.collections.mapNotNull { it.id },
        title = title!!,
        platforms = this.platforms,
        coverId = this.coverImage?.id,
        headerId = this.headerImage?.id,
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
        imageIds = this.images.mapNotNull { it.id },
        videoUrls = this.videoUrls.map { it.toString() },
        metadata = this.metadata.toAdminDto()
    )
}

fun Game.toUserDto(): GameUserDto {
    return GameUserDto(
        id = id!!,
        createdAt = createdAt!!,
        updatedAt = updatedAt!!,
        libraryId = this.library.id!!,
        collectionIds = this.collections.mapNotNull { it.id },
        title = title!!,
        platforms = this.platforms,
        coverId = this.coverImage?.id,
        headerId = this.headerImage?.id,
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
        imageIds = this.images.mapNotNull { it.id },
        videoUrls = this.videoUrls.map { it.toString() },
        metadata = this.metadata.toUserDto()
    )
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