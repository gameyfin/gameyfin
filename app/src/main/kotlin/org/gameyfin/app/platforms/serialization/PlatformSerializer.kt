package org.gameyfin.app.platforms.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import org.gameyfin.pluginapi.gamemetadata.Platform

class PlatformSerializer : JsonSerializer<Platform>() {
    override fun serialize(value: Platform?, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value != null) {
            gen.writeString(value.displayName)
        }
    }
}

