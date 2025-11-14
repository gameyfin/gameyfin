package org.gameyfin.app.platforms.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PlatformSerializerTest {

    private lateinit var serializer: PlatformSerializer
    private lateinit var jsonGenerator: JsonGenerator
    private lateinit var serializerProvider: SerializerProvider

    @BeforeEach
    fun setup() {
        serializer = PlatformSerializer()
        jsonGenerator = mockk(relaxed = true)
        serializerProvider = mockk()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `serialize should write displayName for valid platform`() {
        val platform = Platform.PC_MICROSOFT_WINDOWS

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("PC (Microsoft Windows)") }
    }

    @Test
    fun `serialize should handle null platform value`() {
        serializer.serialize(null, jsonGenerator, serializerProvider)

        verify(exactly = 0) { jsonGenerator.writeString(any<String>()) }
    }

    @Test
    fun `serialize should write correct displayName for PlayStation 5`() {
        val platform = Platform.PLAYSTATION_5

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("PlayStation 5") }
    }

    @Test
    fun `serialize should write correct displayName for Xbox Series X S`() {
        val platform = Platform.XBOX_SERIES_X_S

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("Xbox Series X|S") }
    }

    @Test
    fun `serialize should write correct displayName for Nintendo Switch`() {
        val platform = Platform.NINTENDO_SWITCH

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("Nintendo Switch") }
    }

    @Test
    fun `serialize should handle platforms with special characters in name`() {
        val platform = Platform.ODYSSEY_2_VIDEOPAC_G7000

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("Odyssey 2 / Videopac G7000") }
    }

    @Test
    fun `serialize should handle platforms with numbers in name`() {
        val platform = Platform._3DO_INTERACTIVE_MULTIPLAYER

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("3DO Interactive Multiplayer") }
    }

    @Test
    fun `serialize should handle platforms with hyphens in name`() {
        val platform = Platform.ATARI_8_BIT

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("Atari 8-bit") }
    }

    @Test
    fun `serialize should handle platforms with apostrophes in name`() {
        val platform = Platform.SUPER_ACAN

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("Super A'Can") }
    }

    @Test
    fun `serialize should handle arcade platform`() {
        val platform = Platform.ARCADE

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("Arcade") }
    }

    @Test
    fun `serialize should handle web browser platform`() {
        val platform = Platform.WEB_BROWSER

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("Web browser") }
    }

    @Test
    fun `serialize should handle Android platform`() {
        val platform = Platform.ANDROID

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("Android") }
    }

    @Test
    fun `serialize should handle iOS platform`() {
        val platform = Platform.IOS

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("iOS") }
    }

    @Test
    fun `serialize should handle Linux platform`() {
        val platform = Platform.LINUX

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("Linux") }
    }

    @Test
    fun `serialize should handle Mac platform`() {
        val platform = Platform.MAC

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("Mac") }
    }

    @Test
    fun `serialize should handle DOS platform`() {
        val platform = Platform.DOS

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("DOS") }
    }

    @Test
    fun `serialize should handle Dreamcast platform`() {
        val platform = Platform.DREAMCAST

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("Dreamcast") }
    }

    @Test
    fun `serialize should handle Virtual Boy platform`() {
        val platform = Platform.VIRTUAL_BOY

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("Virtual Boy") }
    }

    @Test
    fun `serialize should handle ZX Spectrum platform`() {
        val platform = Platform.ZX_SPECTRUM

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("ZX Spectrum") }
    }

    @Test
    fun `serialize should handle Game Boy platform`() {
        val platform = Platform.GAME_BOY

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("Game Boy") }
    }

    @Test
    fun `serialize should handle PlayStation VR2 platform`() {
        val platform = Platform.PLAYSTATION_VR2

        serializer.serialize(platform, jsonGenerator, serializerProvider)

        verify(exactly = 1) { jsonGenerator.writeString("PlayStation VR2") }
    }
}

