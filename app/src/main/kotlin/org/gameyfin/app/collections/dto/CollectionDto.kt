package org.gameyfin.app.collections.dto

import com.fasterxml.jackson.annotation.JsonInclude
import org.gameyfin.pluginapi.gamemetadata.Platform
import java.time.Instant

interface CollectionDto {
    val id: Long
    val createdAt: Instant
    val updatedAt: Instant
    val name: String
    val description: String?
    val gameIds: List<Long>?
    val metadata: CollectionMetadataDto?
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CollectionUserDto(
    override val id: Long,
    override val createdAt: Instant,
    override val updatedAt: Instant,
    override val name: String,
    override val description: String?,
    override val gameIds: List<Long> = emptyList(),
    override val metadata: CollectionMetadataDto?,
) : CollectionDto

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CollectionAdminDto(
    override val id: Long,
    override val createdAt: Instant,
    override val updatedAt: Instant,
    override val name: String,
    override val description: String?,
    override val gameIds: List<Long> = emptyList(),
    override val metadata: CollectionMetadataDto?,
    val stats: CollectionStatsDto?,
) : CollectionDto

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CollectionStatsDto(
    val gamesCount: Int,
    val downloadCount: Int,
    val gamePlatforms: Set<Platform>
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CollectionCreateDto(
    val name: String,
    val description: String? = null,
    val gameIds: List<Long>? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CollectionUpdateDto(
    val id: Long,
    val name: String? = null,
    val description: String? = null,
    val gameIds: List<Long>? = null,
    val metadata: CollectionMetadataUpdateDto? = null
)

