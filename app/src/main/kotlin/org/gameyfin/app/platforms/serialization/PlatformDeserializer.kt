package org.gameyfin.app.platforms.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import org.gameyfin.pluginapi.gamemetadata.Platform

class PlatformDeserializer : JsonDeserializer<Platform>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Platform? {
        val displayName = p.text
        return Platform.fromDisplayName(displayName)
    }
}

