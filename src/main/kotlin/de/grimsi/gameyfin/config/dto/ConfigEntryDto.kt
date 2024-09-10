package de.grimsi.gameyfin.config.dto

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.annotation.Nonnull

@JsonInclude(JsonInclude.Include.ALWAYS)
data class ConfigEntryDto(
    @Nonnull val key: String,
    val value: String?,
    val defaultValue: String?,
    @Nonnull val type: String,
    @Nonnull val description: String
)