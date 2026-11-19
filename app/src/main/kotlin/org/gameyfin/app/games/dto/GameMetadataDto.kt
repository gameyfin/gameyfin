package org.gameyfin.app.games.dto

import com.fasterxml.jackson.annotation.JsonInclude

interface GameMetadataDto {
    val fileSize: Long
    val directory: Boolean?
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GameMetadataUserDto(
    override val fileSize: Long,
    override val directory: Boolean? = null
) : GameMetadataDto

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GameMetadataAdminDto(
    val path: String?,
    override val fileSize: Long,
    val fields: Map<String, GameFieldMetadataDto>?,
    val originalIds: Map<String, String>?,
    val downloadCount: Int,
    val matchConfirmed: Boolean,
    override val directory: Boolean? = null
) : GameMetadataDto
