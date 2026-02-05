package org.gameyfin.app.core.serialization

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.gameyfin.pluginapi.gamemetadata.GameFeature
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameFeatureDeserializerTest {

    private lateinit var deserializer: GameFeatureDeserializer
    private lateinit var jsonParser: JsonParser
    private lateinit var deserializationContext: DeserializationContext

    @BeforeEach
    fun setup() {
        deserializer = GameFeatureDeserializer()
        jsonParser = mockk()
        deserializationContext = mockk()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `deserialize should return correct feature for valid displayName`() {
        every { jsonParser.string } returns "Singleplayer"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(GameFeature.SINGLEPLAYER, result)
    }

    @Test
    fun `deserialize should return null for unknown displayName`() {
        every { jsonParser.string } returns "Unknown Feature"

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
        every { jsonParser.string } returns "multiplayer"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertNull(result)
    }

    @Test
    fun `deserialize should handle Multiplayer feature`() {
        every { jsonParser.string } returns "Multiplayer"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(GameFeature.MULTIPLAYER, result)
    }

    @Test
    fun `deserialize should handle Co-op feature`() {
        every { jsonParser.string } returns "Co-op"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(GameFeature.CO_OP, result)
    }

    @Test
    fun `deserialize should handle Cross-Platform feature`() {
        every { jsonParser.string } returns "Cross-Platform"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(GameFeature.CROSS_PLATFORM, result)
    }

    @Test
    fun `deserialize should handle VR feature`() {
        every { jsonParser.string } returns "VR"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(GameFeature.VR, result)
    }

    @Test
    fun `deserialize should handle AR feature`() {
        every { jsonParser.string } returns "AR"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(GameFeature.AR, result)
    }

    @Test
    fun `deserialize should handle Cloud Saves feature`() {
        every { jsonParser.string } returns "Cloud Saves"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(GameFeature.CLOUD_SAVES, result)
    }

    @Test
    fun `deserialize should handle Controller Support feature`() {
        every { jsonParser.string } returns "Controller Support"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(GameFeature.CONTROLLER_SUPPORT, result)
    }

    @Test
    fun `deserialize should handle Local Multiplayer feature`() {
        every { jsonParser.string } returns "Local Multiplayer"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(GameFeature.LOCAL_MULTIPLAYER, result)
    }

    @Test
    fun `deserialize should handle Online Co-op feature`() {
        every { jsonParser.string } returns "Online Co-op"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(GameFeature.ONLINE_CO_OP, result)
    }

    @Test
    fun `deserialize should handle Online PvP feature`() {
        every { jsonParser.string } returns "Online PvP"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(GameFeature.ONLINE_PVP, result)
    }

    @Test
    fun `deserialize should handle Crossplay feature`() {
        every { jsonParser.string } returns "Crossplay"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(GameFeature.CROSSPLAY, result)
    }

    @Test
    fun `deserialize should handle Splitscreen feature`() {
        every { jsonParser.string } returns "Splitscreen"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(GameFeature.SPLITSCREEN, result)
    }

    @Test
    fun `deserialize should handle all valid feature displayNames correctly`() {
        GameFeature.entries.forEach { feature ->
            every { jsonParser.string } returns feature.displayName

            val result = deserializer.deserialize(jsonParser, deserializationContext)

            assertEquals(feature, result, "Failed to deserialize ${feature.displayName}")
        }
    }
}
