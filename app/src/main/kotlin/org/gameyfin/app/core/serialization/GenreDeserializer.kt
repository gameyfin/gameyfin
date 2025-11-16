package org.gameyfin.app.core.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import org.gameyfin.pluginapi.gamemetadata.Genre

/**
 * Jackson deserializer for Genre enum.
 * Deserializes JSON strings by matching against the Genre's displayName property.
 */
class GenreDeserializer : JsonDeserializer<Genre?>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Genre? {
        val displayName = p.text ?: return null

        if (displayName.isEmpty()) {
            return null
        }

        return Genre.entries.firstOrNull { it.displayName == displayName }
    }
}