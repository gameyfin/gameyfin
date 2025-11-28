package org.gameyfin.app.collections.extensions

import org.gameyfin.app.collections.dto.*
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.core.security.isCurrentUserAdmin

fun Collection.toDto(): CollectionDto = if (isCurrentUserAdmin()) this.toAdminDto() else this.toUserDto()

fun Collection.toAdminDto(): CollectionAdminDto = CollectionAdminDto(
    id = id!!,
    createdAt = createdAt!!,
    updatedAt = updatedAt!!,
    name = name,
    description = description,
    gameIds = games.mapNotNull { it.id },
    stats = CollectionStatsDto(
        gamesCount = games.size,
        downloadCount = games.sumOf { it.metadata.downloadCount },
        gamePlatforms = games.flatMap { it.platforms }.toSet()
    )
)

fun Collection.toUserDto(): CollectionUserDto = CollectionUserDto(
    id = id!!,
    createdAt = createdAt!!,
    updatedAt = updatedAt!!,
    name = name,
    description = description,
    gameIds = games.mapNotNull { it.id }
)

fun CollectionCreateDto.toEntity(): Collection = Collection(
    name = name,
    description = description
)

