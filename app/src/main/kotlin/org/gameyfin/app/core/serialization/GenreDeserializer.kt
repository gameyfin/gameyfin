package org.gameyfin.app.core.serialization

import org.gameyfin.pluginapi.gamemetadata.Genre
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer

/**
 * Jackson deserializer for Genre enum.
 * Deserializes JSON strings by matching against the Genre's displayName property.
 */
class GenreDeserializer : ValueDeserializer<Genre?>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Genre? {
        val displayName = p.string ?: return null

        if (displayName.isEmpty()) {
            return null
        }

        return Genre.entries.firstOrNull { it.displayName == displayName }
    }
}