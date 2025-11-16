package org.gameyfin.app.core.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import org.gameyfin.pluginapi.gamemetadata.Platform

/**
 * Jackson deserializer for Platform enum.
 * Deserializes JSON strings by matching against the Platform's displayName property.
 */
class PlatformDeserializer : JsonDeserializer<Platform?>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Platform? {
        val displayName = p.text ?: return null

        if (displayName.isEmpty()) {
            return null
        }

        return Platform.entries.firstOrNull { it.displayName == displayName }
    }
}

