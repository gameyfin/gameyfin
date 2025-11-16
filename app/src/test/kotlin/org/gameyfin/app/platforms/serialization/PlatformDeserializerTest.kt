package org.gameyfin.app.platforms.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.gameyfin.app.core.serialization.PlatformDeserializer
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlatformDeserializerTest {

    private lateinit var deserializer: PlatformDeserializer
    private lateinit var jsonParser: JsonParser
    private lateinit var deserializationContext: DeserializationContext

    @BeforeEach
    fun setup() {
        deserializer = PlatformDeserializer()
        jsonParser = mockk()
        deserializationContext = mockk()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `deserialize should return correct platform for valid displayName`() {
        every { jsonParser.text } returns "PC (Microsoft Windows)"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.PC_MICROSOFT_WINDOWS, result)
    }

    @Test
    fun `deserialize should return null for unknown displayName`() {
        every { jsonParser.text } returns "Unknown Platform"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertNull(result)
    }

    @Test
    fun `deserialize should return null for empty string`() {
        every { jsonParser.text } returns ""

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertNull(result)
    }

    @Test
    fun `deserialize should return correct platform for PlayStation 5`() {
        every { jsonParser.text } returns "PlayStation 5"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.PLAYSTATION_5, result)
    }

    @Test
    fun `deserialize should return correct platform for Xbox Series X S`() {
        every { jsonParser.text } returns "Xbox Series X|S"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.XBOX_SERIES_X_S, result)
    }

    @Test
    fun `deserialize should return correct platform for Nintendo Switch`() {
        every { jsonParser.text } returns "Nintendo Switch"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.NINTENDO_SWITCH, result)
    }

    @Test
    fun `deserialize should be case-sensitive`() {
        every { jsonParser.text } returns "playstation 5"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertNull(result)
    }

    @Test
    fun `deserialize should handle platforms with special characters`() {
        every { jsonParser.text } returns "Odyssey 2 / Videopac G7000"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.ODYSSEY_2_VIDEOPAC_G7000, result)
    }

    @Test
    fun `deserialize should handle platforms with numbers at start`() {
        every { jsonParser.text } returns "3DO Interactive Multiplayer"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform._3DO_INTERACTIVE_MULTIPLAYER, result)
    }

    @Test
    fun `deserialize should handle platforms with hyphens`() {
        every { jsonParser.text } returns "Atari 8-bit"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.ATARI_8_BIT, result)
    }

    @Test
    fun `deserialize should handle platforms with apostrophes`() {
        every { jsonParser.text } returns "Super A'Can"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.SUPER_ACAN, result)
    }

    @Test
    fun `deserialize should return null for whitespace-only string`() {
        every { jsonParser.text } returns "   "

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertNull(result)
    }

    @Test
    fun `deserialize should not trim whitespace from displayName`() {
        every { jsonParser.text } returns " PlayStation 5 "

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertNull(result)
    }

    @Test
    fun `deserialize should handle Arcade platform`() {
        every { jsonParser.text } returns "Arcade"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.ARCADE, result)
    }

    @Test
    fun `deserialize should handle Web browser platform`() {
        every { jsonParser.text } returns "Web browser"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.WEB_BROWSER, result)
    }

    @Test
    fun `deserialize should handle Android platform`() {
        every { jsonParser.text } returns "Android"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.ANDROID, result)
    }

    @Test
    fun `deserialize should handle iOS platform`() {
        every { jsonParser.text } returns "iOS"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.IOS, result)
    }

    @Test
    fun `deserialize should handle Linux platform`() {
        every { jsonParser.text } returns "Linux"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.LINUX, result)
    }

    @Test
    fun `deserialize should handle Mac platform`() {
        every { jsonParser.text } returns "Mac"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.MAC, result)
    }

    @Test
    fun `deserialize should handle DOS platform`() {
        every { jsonParser.text } returns "DOS"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.DOS, result)
    }

    @Test
    fun `deserialize should handle Dreamcast platform`() {
        every { jsonParser.text } returns "Dreamcast"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.DREAMCAST, result)
    }

    @Test
    fun `deserialize should handle Virtual Boy platform`() {
        every { jsonParser.text } returns "Virtual Boy"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.VIRTUAL_BOY, result)
    }

    @Test
    fun `deserialize should handle ZX Spectrum platform`() {
        every { jsonParser.text } returns "ZX Spectrum"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.ZX_SPECTRUM, result)
    }

    @Test
    fun `deserialize should handle Game Boy platform`() {
        every { jsonParser.text } returns "Game Boy"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.GAME_BOY, result)
    }

    @Test
    fun `deserialize should handle PlayStation VR2 platform`() {
        every { jsonParser.text } returns "PlayStation VR2"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.PLAYSTATION_VR2, result)
    }

    @Test
    fun `deserialize should handle Nintendo Entertainment System platform`() {
        every { jsonParser.text } returns "Nintendo Entertainment System"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.NINTENDO_ENTERTAINMENT_SYSTEM, result)
    }

    @Test
    fun `deserialize should handle Super Nintendo Entertainment System platform`() {
        every { jsonParser.text } returns "Super Nintendo Entertainment System"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.SUPER_NINTENDO_ENTERTAINMENT_SYSTEM, result)
    }

    @Test
    fun `deserialize should handle Sega Mega Drive Genesis platform`() {
        every { jsonParser.text } returns "Sega Mega Drive/Genesis"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.SEGA_MEGA_DRIVE_GENESIS, result)
    }

    @Test
    fun `deserialize should handle platforms with long names`() {
        every { jsonParser.text } returns "Call-A-Computer time-shared mainframe computer system"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.CALL_A_COMPUTER_TIME_SHARED_MAINFRAME_COMPUTER_SYSTEM, result)
    }

    @Test
    fun `deserialize should return null for partial match`() {
        every { jsonParser.text } returns "PlayStation"

        val result = deserializer.deserialize(jsonParser, deserializationContext)

        assertEquals(Platform.PLAYSTATION, result)
    }

    @Test
    fun `deserialize should handle all valid platform displayNames correctly`() {
        Platform.entries.forEach { platform ->
            every { jsonParser.text } returns platform.displayName

            val result = deserializer.deserialize(jsonParser, deserializationContext)

            assertEquals(platform, result, "Failed to deserialize ${platform.displayName}")
        }
    }
}

