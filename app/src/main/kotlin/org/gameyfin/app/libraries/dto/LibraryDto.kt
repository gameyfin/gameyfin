package org.gameyfin.app.libraries.dto

import com.fasterxml.jackson.annotation.JsonInclude
import org.gameyfin.pluginapi.gamemetadata.Platform
import java.time.Instant

interface LibraryDto {
    val id: Long
    val name: String
    val createdAt: Instant
    val games: List<Long>?
    val metadata: LibraryMetadataDto
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class LibraryUserDto(
    override val id: Long,
    override val name: String,
    override val createdAt: Instant,
    override val games: List<Long>?,
    override val metadata: LibraryMetadataDto
) : LibraryDto

@JsonInclude(JsonInclude.Include.NON_NULL)
data class LibraryAdminDto(
    override val id: Long,
    override val name: String,
    override val createdAt: Instant,
    val directories: List<DirectoryMappingDto>,
    val platforms: List<Platform>,
    override val games: List<Long>?,
    val stats: LibraryStatsDto?,
    val ignoredPaths: List<IgnoredPathDto>?,
    override val metadata: LibraryMetadataDto
) : LibraryDto
