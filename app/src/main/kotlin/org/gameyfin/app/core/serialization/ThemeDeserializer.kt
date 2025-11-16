package org.gameyfin.app.core.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import org.gameyfin.pluginapi.gamemetadata.Theme

/**
 * Jackson deserializer for Theme enum.
 * Deserializes JSON strings by matching against the Theme's displayName property.
 */
class ThemeDeserializer : JsonDeserializer<Theme?>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Theme? {
        val displayName = p.text ?: return null

        if (displayName.isEmpty()) {
            return null
        }

        return Theme.entries.firstOrNull { it.displayName == displayName }
    }
}

