package org.gameyfin.app.core.plugins.dto

import org.gameyfin.app.core.plugins.management.PluginOrigin
import org.gameyfin.app.core.plugins.management.PluginTrustLevel
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResultType
import org.junit.jupiter.api.Test
import org.pf4j.PluginState
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import tools.jackson.databind.json.JsonMapper

class PluginUpdateDtoTest {

    @Test
    fun `dto should store all properties correctly`() {
        val config = mapOf("key1" to "value1")
        val configValidation = PluginConfigValidationResult(PluginConfigValidationResultType.VALID)

        val dto = PluginUpdateDto(
            id = "test-plugin",
            state = PluginState.STARTED,
            config = config,
            configValidation = configValidation,
            priority = 10
        )

        assertEquals("test-plugin", dto.id)
        assertEquals(PluginState.STARTED, dto.state)
        assertEquals(config, dto.config)
        assertEquals(configValidation, dto.configValidation)
        assertEquals(10, dto.priority)
    }

    @Test
    fun `dto should handle null optional fields`() {
        val dto = PluginUpdateDto(
            id = "test-plugin",
            state = null,
            config = null,
            configValidation = null,
            priority = null
        )

        assertEquals("test-plugin", dto.id)
        assertNull(dto.state)
        assertNull(dto.config)
        assertNull(dto.configValidation)
        assertNull(dto.priority)
    }

    @Test
    fun `dto should handle state update only`() {
        val dto = PluginUpdateDto(
            id = "plugin-id",
            state = PluginState.STOPPED
        )

        assertEquals("plugin-id", dto.id)
        assertEquals(PluginState.STOPPED, dto.state)
        assertNull(dto.config)
        assertNull(dto.configValidation)
        assertNull(dto.priority)
    }

    @Test
    fun `dto should handle config update only`() {
        val config = mapOf("key" to "value")
        val dto = PluginUpdateDto(
            id = "plugin-id",
            config = config
        )

        assertEquals("plugin-id", dto.id)
        assertEquals(config, dto.config)
        assertNull(dto.state)
        assertNull(dto.configValidation)
        assertNull(dto.priority)
    }

    @Test
    fun `dto should handle priority update only`() {
        val dto = PluginUpdateDto(
            id = "plugin-id",
            priority = 5
        )

        assertEquals("plugin-id", dto.id)
        assertEquals(5, dto.priority)
        assertNull(dto.state)
        assertNull(dto.config)
        assertNull(dto.configValidation)
    }

    @Test
    fun `dto should handle configValidation update only`() {
        val validation = PluginConfigValidationResult(PluginConfigValidationResultType.INVALID)
        val dto = PluginUpdateDto(
            id = "plugin-id",
            configValidation = validation
        )

        assertEquals("plugin-id", dto.id)
        assertEquals(validation, dto.configValidation)
        assertNull(dto.state)
        assertNull(dto.config)
        assertNull(dto.priority)
    }

    @Test
    fun `dto should handle empty config`() {
        val dto = PluginUpdateDto(
            id = "plugin-id",
            config = emptyMap()
        )

        assertEquals(emptyMap(), dto.config)
    }

    @Test
    fun `dto should handle config with null values`() {
        val config = mapOf("key1" to "value1", "key2" to null)
        val dto = PluginUpdateDto(
            id = "plugin-id",
            config = config
        )

        assertEquals("value1", dto.config!!["key1"])
        assertNull(dto.config["key2"])
    }

    @Test
    fun `dto should handle different plugin states`() {
        val states = listOf(
            PluginState.CREATED,
            PluginState.DISABLED,
            PluginState.STARTED,
            PluginState.STOPPED,
            PluginState.FAILED
        )

        states.forEach { state ->
            val dto = PluginUpdateDto(id = "plugin", state = state)
            assertEquals(state, dto.state)
        }
    }

    @Test
    fun `dto should have null defaults for plugin and removed`() {
        val dto = PluginUpdateDto(id = "plugin")

        assertNull(dto.state)
        assertNull(dto.config)
        assertNull(dto.configValidation)
        assertNull(dto.priority)
        assertNull(dto.plugin)
        assertNull(dto.removed)
    }

    @Test
    fun `dto should store full plugin data`() {
        val plugin = PluginDto(
            id = "plugin",
            types = listOf("DownloadProvider"),
            name = "Plugin",
            description = "Desc",
            version = "1.0.0",
            author = "Author",
            hasLogo = false,
            state = PluginState.STARTED,
            priority = 1,
            trustLevel = PluginTrustLevel.OFFICIAL,
            origin = PluginOrigin.MARKETPLACE
        )

        val dto = PluginUpdateDto(id = "plugin", plugin = plugin)

        assertEquals(plugin, dto.plugin)
        assertNull(dto.removed)
    }

    @Test
    fun `dto should store removed flag`() {
        val dto = PluginUpdateDto(id = "plugin", removed = true)

        assertEquals(true, dto.removed)
        assertNull(dto.plugin)
        assertNotEquals(dto, PluginUpdateDto(id = "plugin"))
        assertEquals(dto, PluginUpdateDto(id = "plugin", removed = true))
    }

    @Test
    fun `dto json serialization should omit null fields`() {
        val json = JsonMapper.builder().build().writeValueAsString(PluginUpdateDto(id = "plugin", removed = true))

        assertEquals("""{"id":"plugin","removed":true}""", json)
    }
}
