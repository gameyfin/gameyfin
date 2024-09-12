package de.grimsi.gameyfin.config.dto

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.annotation.Nonnull

@JsonInclude(JsonInclude.Include.ALWAYS)
data class ConfigEntryDto(
    @field:Nonnull val key: String,
    val value: String?,
    val defaultValue: String?,
    @field:Nonnull val type: String,
    @field:Nonnull val description: String
)