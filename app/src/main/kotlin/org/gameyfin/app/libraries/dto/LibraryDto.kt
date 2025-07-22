package org.gameyfin.app.libraries.dto

interface LibraryDto {
    val id: Long
    val name: String
    val games: List<Long>?
}

data class LibraryUserDto(
    override val id: Long,
    override val name: String,
    override val games: List<Long>?
) : LibraryDto

data class LibraryAdminDto(
    override val id: Long,
    override val name: String,
    val directories: List<DirectoryMappingDto>,
    override val games: List<Long>?,
    val stats: LibraryStatsDto?,
    val unmatchedPaths: List<String> = emptyList()
) : LibraryDto
