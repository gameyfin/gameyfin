package org.gameyfin.app.core.serialization

import org.gameyfin.pluginapi.gamemetadata.PlayerPerspective
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer

/**
 * Jackson deserializer for PlayerPerspective enum.
 * Deserializes JSON strings by matching against the PlayerPerspective's displayName property.
 */
class PlayerPerspectiveDeserializer : ValueDeserializer<PlayerPerspective?>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PlayerPerspective? {
        val displayName = p.string ?: return null

        if (displayName.isEmpty()) {
            return null
        }

        return PlayerPerspective.entries.firstOrNull { it.displayName == displayName }
    }
}

