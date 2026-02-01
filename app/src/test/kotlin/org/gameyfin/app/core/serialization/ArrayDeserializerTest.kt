package org.gameyfin.app.core.serialization

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.core.JsonParser
import tools.jackson.core.ObjectReadContext
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import java.io.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArrayDeserializerTest {

    private lateinit var deserializer: ArrayDeserializer
    private lateinit var jsonParser: JsonParser
    private lateinit var deserializationContext: DeserializationContext
    private lateinit var objectReadContext: ObjectReadContext

    @BeforeEach
    fun setup() {
        deserializer = ArrayDeserializer()
        jsonParser = mockk()
        deserializationContext = mockk()
        objectReadContext = mockk()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `deserialize should convert JSON array to String array`() {
        val textNode1 = mockk<JsonNode>()
        val textNode2 = mockk<JsonNode>()
        val textNode3 = mockk<JsonNode>()
        every { textNode1.asString() } returns "item1"
        every { textNode2.asString() } returns "item2"
        every { textNode3.asString() } returns "item3"

        val arrayNode = mockk<JsonNode>()
        every { arrayNode.isArray } returns true
        every { arrayNode.iterator() } returns mutableListOf(textNode1, textNode2, textNode3).iterator()
        every { jsonParser.objectReadContext() } returns objectReadContext
        every { objectReadContext.readTree<JsonNode>(jsonParser) } returns arrayNode

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertTrue(result is Array<*>)
        assertEquals(3, result.size)
        assertEquals("item1", result[0])
        assertEquals("item2", result[1])
        assertEquals("item3", result[2])
    }

    @Test
    fun `deserialize should convert empty JSON array to empty String array`() {
        val arrayNode = mockk<JsonNode>()
        every { arrayNode.isArray } returns true
        every { arrayNode.iterator() } returns mutableListOf<JsonNode>().iterator()
        every { jsonParser.objectReadContext() } returns objectReadContext
        every { objectReadContext.readTree<JsonNode>(jsonParser) } returns arrayNode

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertTrue(result is Array<*>)
        assertEquals(0, result.size)
    }

    @Test
    fun `deserialize should handle non-array JSON node`() {
        val textNode = mockk<JsonNode>()
        val serializable = "test string" as Serializable
        every { textNode.isArray } returns false
        every { jsonParser.objectReadContext() } returns objectReadContext
        every { objectReadContext.readTree<JsonNode>(jsonParser) } returns textNode
        every { deserializationContext.readTreeAsValue(textNode, Serializable::class.java) } returns serializable

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(serializable, result)
    }

    @Test
    fun `deserialize should handle array with single element`() {
        val textNode = mockk<JsonNode>()
        every { textNode.asString() } returns "single"

        val arrayNode = mockk<JsonNode>()
        every { arrayNode.isArray } returns true
        every { arrayNode.iterator() } returns mutableListOf(textNode).iterator()
        every { jsonParser.objectReadContext() } returns objectReadContext
        every { objectReadContext.readTree<JsonNode>(jsonParser) } returns arrayNode

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertTrue(result is Array<*>)
        assertEquals(1, result.size)
        assertEquals("single", result[0])
    }
}
