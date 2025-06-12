package de.grimsi.gameyfin.games.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
class GameMetadataDto(
    val path: String?,
    val fileSize: Long,
    val fields: Map<String, GameFieldMetadataDto>?,
    val originalIds: Map<String, String>?,
    val downloadCount: Int,
    val matchConfirmed: Boolean
)