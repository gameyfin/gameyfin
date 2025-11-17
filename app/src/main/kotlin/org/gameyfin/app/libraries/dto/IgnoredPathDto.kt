package org.gameyfin.app.libraries.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class IgnoredPathDto(
    val id: Long,
    val path: String,
    val sourceType: IgnoredPathSourceTypeDto,
    val source: String
)

enum class IgnoredPathSourceTypeDto {
    PLUGIN,
    USER
}

