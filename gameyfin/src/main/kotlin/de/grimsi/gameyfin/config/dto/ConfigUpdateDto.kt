package de.grimsi.gameyfin.config.dto

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import de.grimsi.gameyfin.core.serialization.ArrayDeserializer
import java.io.Serializable

data class ConfigUpdateDto(
    @get:JsonDeserialize(contentUsing = ArrayDeserializer::class)
    val updates: Map<String, Serializable?>
)