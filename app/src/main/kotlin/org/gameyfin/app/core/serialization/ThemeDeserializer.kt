package org.gameyfin.app.core.serialization

import org.gameyfin.pluginapi.gamemetadata.Theme
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer

/**
 * Jackson deserializer for Theme enum.
 * Deserializes JSON strings by matching against the Theme's displayName property.
 */
class ThemeDeserializer : ValueDeserializer<Theme?>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Theme? {
        val displayName = p.string ?: return null

        if (displayName.isEmpty()) {
            return null
        }

        return Theme.entries.firstOrNull { it.displayName == displayName }
    }
}

