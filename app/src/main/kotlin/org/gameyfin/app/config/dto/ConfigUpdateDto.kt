package org.gameyfin.app.config.dto

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import org.gameyfin.app.core.serialization.ArrayDeserializer
import java.io.Serializable

data class ConfigUpdateDto(
    @get:JsonDeserialize(contentUsing = ArrayDeserializer::class)
    val updates: Map<String, Serializable?>
)