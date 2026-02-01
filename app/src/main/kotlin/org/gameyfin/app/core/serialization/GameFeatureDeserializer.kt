package org.gameyfin.app.core.serialization

import org.gameyfin.pluginapi.gamemetadata.GameFeature
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer

/**
 * Jackson deserializer for GameFeature enum.
 * Deserializes JSON strings by matching against the GameFeature's displayName property.
 */
class GameFeatureDeserializer : ValueDeserializer<GameFeature?>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): GameFeature? {
        val displayName = p.string ?: return null

        if (displayName.isEmpty()) {
            return null
        }

        return GameFeature.entries.firstOrNull { it.displayName == displayName }
    }
}

