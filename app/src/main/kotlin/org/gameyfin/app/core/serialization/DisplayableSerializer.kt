package org.gameyfin.app.core.serialization

import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer


/**
 * A generic Jackson serializer for enums that have a displayName property.
 * This serializer writes the displayName value instead of the enum constant name.
 */
class DisplayableSerializer : ValueSerializer<Any>() {
    override fun serialize(value: Any?, gen: JsonGenerator, serializers: SerializationContext) {
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

