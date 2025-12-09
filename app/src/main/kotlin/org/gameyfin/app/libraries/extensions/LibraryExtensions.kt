package org.gameyfin.app.libraries.extensions

import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.libraries.dto.*
import org.gameyfin.app.libraries.entities.Library


fun Library.toDto(): LibraryDto {
    return if (isCurrentUserAdmin()) {
        this.toAdminDto()
    } else {
        this.toUserDto()
    }
}

fun Collection<Library>.toDtos(): List<LibraryDto> {
    return if (isCurrentUserAdmin()) {
        this.map { it.toAdminDto() }
    } else {
        this.map { it.toUserDto() }
    }
}

fun Library.toUserDto(): LibraryUserDto {
    return LibraryUserDto(
        id = this.id!!,
        name = this.name,
        games = this.games.mapNotNull { it.id }
    )
}

fun Library.toAdminDto(): LibraryAdminDto {
    return LibraryAdminDto(
        id = this.id!!,
        name = this.name,
        directories = this.directories.map { DirectoryMappingDto(it.internalPath, it.externalPath) },
        platforms = this.platforms,
        games = this.games.mapNotNull { it.id },
        stats = LibraryStatsDto(
            gamesCount = this.games.size,
            downloadedGamesCount = this.games.sumOf { it.metadata.downloadCount }
        ),
        ignoredPaths = this.ignoredPaths.toDtos()
    )
}
