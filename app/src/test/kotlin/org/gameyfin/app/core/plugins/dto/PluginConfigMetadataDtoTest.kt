package org.gameyfin.app.core.plugins.dto

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginConfigMetadataDtoTest {

    @Test
    fun `dto should store all properties correctly`() {
        val allowedValues = listOf("value1", "value2", "value3")
        val dto = PluginConfigMetadataDto(
            key = "test-key",
            type = "String",
            label = "Test Label",
            description = "Test Description",
            default = "default-value",
            secret = true,
            required = true,
            allowedValues = allowedValues
        )

        assertEquals("test-key", dto.key)
        assertEquals("String", dto.type)
        assertEquals("Test Label", dto.label)
        assertEquals("Test Description", dto.description)
        assertEquals("default-value", dto.default)
        assertTrue(dto.secret)
        assertTrue(dto.required)
        assertEquals(allowedValues, dto.allowedValues)
    }

    @Test
    fun `dto should handle null default value`() {
        val dto = PluginConfigMetadataDto(
            key = "test-key",
            type = "String",
            label = "Test Label",
            description = "Test Description",
            default = null,
            secret = false,
            required = false,
            allowedValues = null
        )

        assertNull(dto.default)
    }

    @Test
    fun `dto should handle null allowedValues`() {
        val dto = PluginConfigMetadataDto(
            key = "test-key",
            type = "String",
            label = "Test Label",
            description = "Test Description",
            default = "default",
            secret = false,
            required = false,
            allowedValues = null
        )

        assertNull(dto.allowedValues)
    }

    @Test
    fun `dto should handle empty allowedValues list`() {
        val dto = PluginConfigMetadataDto(
            key = "test-key",
            type = "String",
            label = "Test Label",
            description = "Test Description",
            default = "default",
            secret = false,
            required = false,
            allowedValues = emptyList()
        )

        assertEquals(emptyList(), dto.allowedValues)
    }

    @Test
    fun `dto should handle boolean type`() {
        val dto = PluginConfigMetadataDto(
            key = "enabled",
            type = "Boolean",
            label = "Enabled",
            description = "Enable feature",
            default = true,
            secret = false,
            required = true,
            allowedValues = null
        )

        assertEquals("Boolean", dto.type)
        assertEquals(true, dto.default)
    }

    @Test
    fun `dto should handle integer type`() {
        val dto = PluginConfigMetadataDto(
            key = "timeout",
            type = "Integer",
            label = "Timeout",
            description = "Timeout in seconds",
            default = 30,
            secret = false,
            required = false,
            allowedValues = null
        )

        assertEquals("Integer", dto.type)
        assertEquals(30, dto.default)
    }

    @Test
    fun `dto should mark sensitive data as secret`() {
        val dto = PluginConfigMetadataDto(
            key = "api-key",
            type = "String",
            label = "API Key",
            description = "Secret API key",
            default = null,
            secret = true,
            required = true,
            allowedValues = null
        )

        assertTrue(dto.secret)
    }

    @Test
    fun `dto should mark optional fields as not required`() {
        val dto = PluginConfigMetadataDto(
            key = "optional-field",
            type = "String",
            label = "Optional",
            description = "Optional field",
            default = null,
            secret = false,
            required = false,
            allowedValues = null
        )

        assertFalse(dto.required)
    }
}

