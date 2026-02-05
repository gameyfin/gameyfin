package org.gameyfin.app.core.serialization

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.gameyfin.pluginapi.gamemetadata.Genre
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GenreDeserializerTest {

    private lateinit var deserializer: GenreDeserializer
    private lateinit var jsonParser: JsonParser
    private lateinit var deserializationContext: DeserializationContext

    @BeforeEach
    fun setup() {
        deserializer = GenreDeserializer()
        jsonParser = mockk()
        deserializationContext = mockk()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `deserialize should return correct genre for valid displayName`() {
        every { jsonParser.string } returns "Action"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Genre.ACTION, result)
    }

    @Test
    fun `deserialize should return null for unknown displayName`() {
        every { jsonParser.string } returns "Unknown Genre"

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
    fun `deserialize should handle Visual Novel genre`() {
        every { jsonParser.string } returns "Visual Novel"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Genre.VISUAL_NOVEL, result)
    }

    @Test
    fun `deserialize should handle Card & Board Game genre`() {
        every { jsonParser.string } returns "Card & Board Game"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Genre.CARD_AND_BOARD_GAME, result)
    }

    @Test
    fun `deserialize should handle Point-and-Click genre`() {
        every { jsonParser.string } returns "Point-and-Click"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Genre.POINT_AND_CLICK, result)
    }

    @Test
    fun `deserialize should handle Real-Time Strategy genre`() {
        every { jsonParser.string } returns "Real-Time Strategy"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Genre.REAL_TIME_STRATEGY, result)
    }

    @Test
    fun `deserialize should handle Turn-Based Strategy genre`() {
        every { jsonParser.string } returns "Turn-Based Strategy"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Genre.TURN_BASED_STRATEGY, result)
    }

    @Test
    fun `deserialize should handle Hack and Slash Beat em up genre`() {
        every { jsonParser.string } returns "Hack and Slash/Beat 'em up"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Genre.HACK_AND_SLASH_BEAT_EM_UP, result)
    }

    @Test
    fun `deserialize should handle Quiz Trivia genre`() {
        every { jsonParser.string } returns "Quiz/Trivia"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Genre.QUIZ_TRIVIA, result)
    }

    @Test
    fun `deserialize should handle Role-Playing genre`() {
        every { jsonParser.string } returns "Role-Playing"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Genre.ROLE_PLAYING, result)
    }

    @Test
    fun `deserialize should handle MOBA genre`() {
        every { jsonParser.string } returns "MOBA"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Genre.MOBA, result)
    }

    @Test
    fun `deserialize should handle MMO genre`() {
        every { jsonParser.string } returns "MMO"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Genre.MMO, result)
    }

    @Test
    fun `deserialize should handle all valid genre displayNames correctly`() {
        Genre.entries.forEach { genre ->
            every { jsonParser.string } returns genre.displayName

            val result = deserializer.deserialize(jsonParser, deserializationContext)

            assertEquals(genre, result, "Failed to deserialize ${genre.displayName}")
        }
    }
}
