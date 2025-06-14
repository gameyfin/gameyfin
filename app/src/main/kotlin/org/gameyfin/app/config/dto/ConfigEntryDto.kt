package org.gameyfin.app.config.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.io.Serializable

@JsonInclude(JsonInclude.Include.ALWAYS)
data class ConfigEntryDto(
    val key: String,
    val description: String,
    val value: Serializable?,
    val defaultValue: Serializable?,
    val type: String,
    val elementType: String?,
    val allowedValues: List<String>?
)