package org.gameyfin.app.core.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import org.gameyfin.pluginapi.gamemetadata.GameFeature

/**
 * Jackson deserializer for GameFeature enum.
 * Deserializes JSON strings by matching against the GameFeature's displayName property.
 */
class GameFeatureDeserializer : JsonDeserializer<GameFeature?>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): GameFeature? {
        val displayName = p.text ?: return null

        if (displayName.isEmpty()) {
            return null
        }

        return GameFeature.entries.firstOrNull { it.displayName == displayName }
    }
}

