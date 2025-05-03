package de.grimsi.gameyfin.core.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import java.io.Serializable

class ArrayDeserializer : JsonDeserializer<Serializable>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Serializable {
        val node = p.codec.readTree<JsonNode>(p)
        return if (node.isArray) {
            node.map { it.asText() }.toTypedArray()
        } else {
            p.codec.treeToValue(node, Serializable::class.java)
        }
    }
}