package org.gameyfin.app.core.serialization

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import java.io.Serializable

class ArrayDeserializer : ValueDeserializer<Serializable>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Serializable {
        val node = p.objectReadContext().readTree<JsonNode>(p)
        return if (node.isArray) {
            node.map { it.asString() }.toTypedArray()
        } else {
            ctxt.readTreeAsValue(node, Serializable::class.java)
        }
    }
}