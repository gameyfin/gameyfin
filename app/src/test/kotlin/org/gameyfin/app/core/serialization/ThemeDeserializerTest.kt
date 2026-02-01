package org.gameyfin.app.core.serialization

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.gameyfin.pluginapi.gamemetadata.Theme
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThemeDeserializerTest {

    private lateinit var deserializer: ThemeDeserializer
    private lateinit var jsonParser: JsonParser
    private lateinit var deserializationContext: DeserializationContext

    @BeforeEach
    fun setup() {
        deserializer = ThemeDeserializer()
        jsonParser = mockk()
        deserializationContext = mockk()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `deserialize should return correct theme for valid displayName`() {
        every { jsonParser.string } returns "Action"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Theme.ACTION, result)
    }

    @Test
    fun `deserialize should return null for unknown displayName`() {
        every { jsonParser.string } returns "Unknown Theme"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertNull(result)
    }

    @Test
    fun `deserialize should return null for empty string`() {
        every { jsonParser.string } returns ""

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertNull(result)
    }

    @Test
    fun `deserialize should return null for null string`() {
        every { jsonParser.string } returns null

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertNull(result)
    }

    @Test
    fun `deserialize should be case-sensitive`() {
        every { jsonParser.string } returns "action"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertNull(result)
    }

    @Test
    fun `deserialize should handle Science Fiction theme`() {
        every { jsonParser.string } returns "Science Fiction"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Theme.SCIENCE_FICTION, result)
    }

    @Test
    fun `deserialize should handle Non-Fiction theme`() {
        every { jsonParser.string } returns "Non-Fiction"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Theme.NON_FICTION, result)
    }

    @Test
    fun `deserialize should handle 4X theme`() {
        every { jsonParser.string } returns "4X"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Theme.FOUR_X, result)
    }

    @Test
    fun `deserialize should handle Open World theme`() {
        every { jsonParser.string } returns "Open World"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Theme.OPEN_WORLD, result)
    }

    @Test
    fun `deserialize should handle Horror theme`() {
        every { jsonParser.string } returns "Horror"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Theme.HORROR, result)
    }

    @Test
    fun `deserialize should handle Fantasy theme`() {
        every { jsonParser.string } returns "Fantasy"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Theme.FANTASY, result)
    }

    @Test
    fun `deserialize should handle Survival theme`() {
        every { jsonParser.string } returns "Survival"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Theme.SURVIVAL, result)
    }

    @Test
    fun `deserialize should handle all valid theme displayNames correctly`() {
        Theme.entries.forEach { theme ->
            every { jsonParser.string } returns theme.displayName

            val result = deserializer.deserialize(jsonParser, deserializationContext)

            assertEquals(theme, result, "Failed to deserialize ${theme.displayName}")
        }
    }
}
