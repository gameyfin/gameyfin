package org.gameyfin.app.core.serialization

import org.gameyfin.pluginapi.gamemetadata.Platform
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer

/**
 * Jackson deserializer for Platform enum.
 * Deserializes JSON strings by matching against the Platform's displayName property.
 */
class PlatformDeserializer : ValueDeserializer<Platform?>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Platform? {
        val displayName = p.string ?: return null

        if (displayName.isEmpty()) {
            return null
        }

        return Platform.entries.firstOrNull { it.displayName == displayName }
    }
}

