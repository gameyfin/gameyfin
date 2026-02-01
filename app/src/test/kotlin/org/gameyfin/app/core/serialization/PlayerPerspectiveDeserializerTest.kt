package org.gameyfin.app.core.serialization

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.gameyfin.pluginapi.gamemetadata.PlayerPerspective
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerPerspectiveDeserializerTest {

    private lateinit var deserializer: PlayerPerspectiveDeserializer
    private lateinit var jsonParser: JsonParser
    private lateinit var deserializationContext: DeserializationContext

    @BeforeEach
    fun setup() {
        deserializer = PlayerPerspectiveDeserializer()
        jsonParser = mockk()
        deserializationContext = mockk()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `deserialize should return correct perspective for valid displayName`() {
        every { jsonParser.string } returns "First-Person"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(PlayerPerspective.FIRST_PERSON, result)
    }

    @Test
    fun `deserialize should return null for unknown displayName`() {
        every { jsonParser.string } returns "Unknown Perspective"

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
        every { jsonParser.string } returns "first-person"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertNull(result)
    }

    @Test
    fun `deserialize should handle Third-Person perspective`() {
        every { jsonParser.string } returns "Third-Person"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(PlayerPerspective.THIRD_PERSON, result)
    }

    @Test
    fun `deserialize should handle Bird View Isometric perspective`() {
        every { jsonParser.string } returns "Bird View/Isometric"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(PlayerPerspective.BIRD_VIEW_ISOMETRIC, result)
    }

    @Test
    fun `deserialize should handle Side View perspective`() {
        every { jsonParser.string } returns "Side View"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(PlayerPerspective.SIDE_VIEW, result)
    }

    @Test
    fun `deserialize should handle Text perspective`() {
        every { jsonParser.string } returns "Text"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(PlayerPerspective.TEXT, result)
    }

    @Test
    fun `deserialize should handle Auditory perspective`() {
        every { jsonParser.string } returns "Auditory"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(PlayerPerspective.AUDITORY, result)
    }

    @Test
    fun `deserialize should handle Virtual Reality perspective`() {
        every { jsonParser.string } returns "Virtual Reality"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(PlayerPerspective.VIRTUAL_REALITY, result)
    }

    @Test
    fun `deserialize should handle Unknown perspective`() {
        every { jsonParser.string } returns "Unknown"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(PlayerPerspective.UNKNOWN, result)
    }

    @Test
    fun `deserialize should handle all valid perspective displayNames correctly`() {
        PlayerPerspective.entries.forEach { perspective ->
            every { jsonParser.string } returns perspective.displayName

            val result = deserializer.deserialize(jsonParser, deserializationContext)

            assertEquals(perspective, result, "Failed to deserialize ${perspective.displayName}")
        }
    }
}
