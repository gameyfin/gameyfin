package org.gameyfin.app.core.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider

/**
 * A generic Jackson serializer for enums that have a displayName property.
 * This serializer writes the displayName value instead of the enum constant name.
 */
class DisplayableSerializer : JsonSerializer<Any>() {
    override fun serialize(value: Any?, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value == null) {
            return
        }

        // Use reflection to get the displayName property
        val displayName = value::class.java.getDeclaredField("displayName").apply {
            isAccessible = true
        }.get(value) as String

        gen.writeString(displayName)
    }
}

