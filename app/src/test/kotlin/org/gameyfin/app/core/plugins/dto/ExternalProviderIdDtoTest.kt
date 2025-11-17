package org.gameyfin.app.core.plugins.dto

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ExternalProviderIdDtoTest {

    @Test
    fun `toString should return formatted string with pluginId and externalProviderId`() {
        val dto = ExternalProviderIdDto("test-plugin", "external-id-123")

        val result = dto.toString()

        assertEquals("test-plugin:external-id-123", result)
    }

    @Test
    fun `toString should handle special characters in pluginId`() {
        val dto = ExternalProviderIdDto("test-plugin.v1", "external-id")

        val result = dto.toString()

        assertEquals("test-plugin.v1:external-id", result)
    }

    @Test
    fun `toString should handle special characters in externalProviderId`() {
        val dto = ExternalProviderIdDto("plugin", "external:id:with:colons")

        val result = dto.toString()

        assertEquals("plugin:external:id:with:colons", result)
    }

    @Test
    fun `toString should handle empty strings`() {
        val dto = ExternalProviderIdDto("", "")

        val result = dto.toString()

        assertEquals(":", result)
    }

    @Test
    fun `dto should store pluginId correctly`() {
        val dto = ExternalProviderIdDto("my-plugin", "provider-123")

        assertEquals("my-plugin", dto.pluginId)
    }

    @Test
    fun `dto should store externalProviderId correctly`() {
        val dto = ExternalProviderIdDto("my-plugin", "provider-123")

        assertEquals("provider-123", dto.externalProviderId)
    }
}