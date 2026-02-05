package org.gameyfin.app.core.serialization

import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.gameyfin.pluginapi.gamemetadata.GameFeature
import org.gameyfin.pluginapi.gamemetadata.Genre
import org.gameyfin.pluginapi.gamemetadata.PlayerPerspective
import org.gameyfin.pluginapi.gamemetadata.Theme
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext

class DisplayableSerializerTest {

    private lateinit var serializer: DisplayableSerializer
    private lateinit var jsonGenerator: JsonGenerator
    private lateinit var serializationContext: SerializationContext

    @BeforeEach
    fun setup() {
        serializer = DisplayableSerializer()
        jsonGenerator = mockk(relaxed = true)
        serializationContext = mockk()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `serialize should write displayName for valid theme`() {
        val theme = Theme.SCIENCE_FICTION

        serializer.serialize(theme, jsonGenerator, serializationContext)

        verify(exactly = 1) { jsonGenerator.writeString("Science Fiction") }
    }

    @Test
    fun `serialize should handle null value`() {
        serializer.serialize(null, jsonGenerator, serializationContext)

        verify(exactly = 0) { jsonGenerator.writeString(any<String>()) }
    }

    @Test
    fun `serialize should write displayName for valid genre`() {
        val genre = Genre.ROLE_PLAYING

        serializer.serialize(genre, jsonGenerator, serializationContext)

        verify(exactly = 1) { jsonGenerator.writeString("Role-Playing") }
    }

    @Test
    fun `serialize should write displayName for valid game feature`() {
        val feature = GameFeature.MULTIPLAYER

        serializer.serialize(feature, jsonGenerator, serializationContext)

        verify(exactly = 1) { jsonGenerator.writeString("Multiplayer") }
    }

    @Test
    fun `serialize should write displayName for valid player perspective`() {
        val perspective = PlayerPerspective.FIRST_PERSON

        serializer.serialize(perspective, jsonGenerator, serializationContext)

        verify(exactly = 1) { jsonGenerator.writeString("First-Person") }
    }

    @Test
    fun `serialize should handle theme with hyphens`() {
        val theme = Theme.NON_FICTION

        serializer.serialize(theme, jsonGenerator, serializationContext)

        verify(exactly = 1) { jsonGenerator.writeString("Non-Fiction") }
    }

    @Test
    fun `serialize should handle genre with ampersand`() {
        val genre = Genre.CARD_AND_BOARD_GAME

        serializer.serialize(genre, jsonGenerator, serializationContext)

        verify(exactly = 1) { jsonGenerator.writeString("Card & Board Game") }
    }

    @Test
    fun `serialize should handle genre with slash and apostrophe`() {
        val genre = Genre.HACK_AND_SLASH_BEAT_EM_UP

        serializer.serialize(genre, jsonGenerator, serializationContext)

        verify(exactly = 1) { jsonGenerator.writeString("Hack and Slash/Beat 'em up") }
    }

    @Test
    fun `serialize should handle feature with hyphen`() {
        val feature = GameFeature.CROSS_PLATFORM

        serializer.serialize(feature, jsonGenerator, serializationContext)

        verify(exactly = 1) { jsonGenerator.writeString("Cross-Platform") }
    }

    @Test
    fun `serialize should handle perspective with slash`() {
        val perspective = PlayerPerspective.BIRD_VIEW_ISOMETRIC

        serializer.serialize(perspective, jsonGenerator, serializationContext)

        verify(exactly = 1) { jsonGenerator.writeString("Bird View/Isometric") }
    }

    @Test
    fun `serialize should handle all theme values correctly`() {
        Theme.entries.forEach { theme ->
            serializer.serialize(theme, jsonGenerator, serializationContext)

            verify(exactly = 1) { jsonGenerator.writeString(theme.displayName) }
        }
    }

    @Test
    fun `serialize should handle all genre values correctly`() {
        Genre.entries.forEach { genre ->
            serializer.serialize(genre, jsonGenerator, serializationContext)

            verify(atLeast = 1) { jsonGenerator.writeString(genre.displayName) }
        }
    }

    @Test
    fun `serialize should handle all game feature values correctly`() {
        GameFeature.entries.forEach { feature ->
            serializer.serialize(feature, jsonGenerator, serializationContext)

            verify(atLeast = 1) { jsonGenerator.writeString(feature.displayName) }
        }
    }

    @Test
    fun `serialize should handle all player perspective values correctly`() {
        PlayerPerspective.entries.forEach { perspective ->
            serializer.serialize(perspective, jsonGenerator, serializationContext)

            verify(atLeast = 1) { jsonGenerator.writeString(perspective.displayName) }
        }
    }
}
