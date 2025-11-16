package org.gameyfin.app.core.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import org.gameyfin.pluginapi.gamemetadata.PlayerPerspective

/**
 * Jackson deserializer for PlayerPerspective enum.
 * Deserializes JSON strings by matching against the PlayerPerspective's displayName property.
 */
class PlayerPerspectiveDeserializer : JsonDeserializer<PlayerPerspective?>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PlayerPerspective? {
        val displayName = p.text ?: return null

        if (displayName.isEmpty()) {
            return null
        }

        return PlayerPerspective.entries.firstOrNull { it.displayName == displayName }
    }
}

