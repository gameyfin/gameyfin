package de.grimsi.gameyfin.config.dto

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import de.grimsi.gameyfin.core.serialization.ArrayDeserializer
import java.io.Serializable

data class ConfigValuePairDto(
    val key: String,

    @field:JsonDeserialize(using = ArrayDeserializer::class)
    val value: Serializable?
)