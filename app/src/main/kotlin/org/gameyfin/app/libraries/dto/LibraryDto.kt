package org.gameyfin.app.libraries.dto

import com.fasterxml.jackson.annotation.JsonInclude
import org.gameyfin.pluginapi.gamemetadata.Platform

interface LibraryDto {
    val id: Long
    val name: String
    val games: List<Long>?
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class LibraryUserDto(
    override val id: Long,
    override val name: String,
    override val games: List<Long>?
) : LibraryDto

@JsonInclude(JsonInclude.Include.NON_NULL)
data class LibraryAdminDto(
    override val id: Long,
    override val name: String,
    val directories: List<DirectoryMappingDto>,
    val platforms: List<Platform>,
    override val games: List<Long>?,
    val stats: LibraryStatsDto?,
    val unmatchedPaths: List<String> = emptyList()
) : LibraryDto
