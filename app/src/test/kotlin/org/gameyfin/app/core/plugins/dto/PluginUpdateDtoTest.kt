package org.gameyfin.app.core.plugins.dto

import org.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResultType
import org.junit.jupiter.api.Test
import org.pf4j.PluginState
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}