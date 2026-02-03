package org.gameyfin.app.config.dto

import org.gameyfin.app.core.serialization.ArrayDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import java.io.Serializable

data class ConfigUpdateDto(
    @get:JsonDeserialize(contentUsing = ArrayDeserializer::class)
    val updates: Map<String, Serializable?>
)