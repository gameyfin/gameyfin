package org.gameyfin.app.core.plugins.dto

import org.gameyfin.app.core.plugins.management.PluginTrustLevel
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResultType
import org.junit.jupiter.api.Test
import org.pf4j.PluginState
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginDtoTest {

    @Test
    fun `dto should store all properties correctly`() {
        val types = listOf("DownloadProvider", "MetadataProvider")
        val configMetadata = listOf(
            PluginConfigMetadataDto("key1", "String", "Label 1", "Desc 1", null, false, true, null)
        )
        val config = mapOf("key1" to "value1")
        val configValidation = PluginConfigValidationResult(PluginConfigValidationResultType.VALID)

        val dto = PluginDto(
            id = "test-plugin",
            types = types,
            name = "Test Plugin",
            description = "Test Description",
            shortDescription = "Short Desc",
            version = "1.0.0",
            author = "Test Author",
            license = "MIT",
            url = "https://example.com",
            hasLogo = true,
            state = PluginState.STARTED,
            configMetadata = configMetadata,
            config = config,
            configValidation = configValidation,
            priority = 10,
            trustLevel = PluginTrustLevel.OFFICIAL
        )

        assertEquals("test-plugin", dto.id)
        assertEquals(types, dto.types)
        assertEquals("Test Plugin", dto.name)
        assertEquals("Test Description", dto.description)
        assertEquals("Short Desc", dto.shortDescription)
        assertEquals("1.0.0", dto.version)
        assertEquals("Test Author", dto.author)
        assertEquals("MIT", dto.license)
        assertEquals("https://example.com", dto.url)
        assertTrue(dto.hasLogo)
        assertEquals(PluginState.STARTED, dto.state)
        assertEquals(configMetadata, dto.configMetadata)
        assertEquals(config, dto.config)
        assertEquals(configValidation, dto.configValidation)
        assertEquals(10, dto.priority)
        assertEquals(PluginTrustLevel.OFFICIAL, dto.trustLevel)
    }

    @Test
    fun `dto should handle null optional fields`() {
        val dto = PluginDto(
            id = "test-plugin",
            types = emptyList(),
            name = "Test Plugin",
            description = "Test Description",
            shortDescription = null,
            version = "1.0.0",
            author = "Test Author",
            license = null,
            url = null,
            hasLogo = false,
            state = PluginState.CREATED,
            configMetadata = null,
            config = null,
            configValidation = null,
            priority = 1,
            trustLevel = PluginTrustLevel.UNKNOWN
        )

        assertNull(dto.shortDescription)
        assertNull(dto.license)
        assertNull(dto.url)
        assertNull(dto.configMetadata)
        assertNull(dto.config)
        assertNull(dto.configValidation)
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
            val dto = PluginDto(
                id = "plugin",
                types = emptyList(),
                name = "Plugin",
                description = "Desc",
                version = "1.0.0",
                author = "Author",
                hasLogo = false,
                state = state,
                priority = 1,
                trustLevel = PluginTrustLevel.BUNDLED
            )

            assertEquals(state, dto.state)
        }
    }

    @Test
    fun `dto should handle different trust levels`() {
        val trustLevels = listOf(
            PluginTrustLevel.BUNDLED,
            PluginTrustLevel.OFFICIAL,
            PluginTrustLevel.THIRD_PARTY,
            PluginTrustLevel.UNTRUSTED,
            PluginTrustLevel.UNKNOWN
        )

        trustLevels.forEach { trustLevel ->
            val dto = PluginDto(
                id = "plugin",
                types = emptyList(),
                name = "Plugin",
                description = "Desc",
                version = "1.0.0",
                author = "Author",
                hasLogo = false,
                state = PluginState.STARTED,
                priority = 1,
                trustLevel = trustLevel
            )

            assertEquals(trustLevel, dto.trustLevel)
        }
    }

    @Test
    fun `dto should handle empty types list`() {
        val dto = PluginDto(
            id = "plugin",
            types = emptyList(),
            name = "Plugin",
            description = "Desc",
            version = "1.0.0",
            author = "Author",
            hasLogo = false,
            state = PluginState.STARTED,
            priority = 1,
            trustLevel = PluginTrustLevel.BUNDLED
        )

        assertTrue(dto.types.isEmpty())
    }

    @Test
    fun `dto should handle multiple types`() {
        val types = listOf("Type1", "Type2", "Type3")
        val dto = PluginDto(
            id = "plugin",
            types = types,
            name = "Plugin",
            description = "Desc",
            version = "1.0.0",
            author = "Author",
            hasLogo = false,
            state = PluginState.STARTED,
            priority = 1,
            trustLevel = PluginTrustLevel.BUNDLED
        )

        assertEquals(3, dto.types.size)
        assertTrue(dto.types.contains("Type1"))
        assertTrue(dto.types.contains("Type2"))
        assertTrue(dto.types.contains("Type3"))
    }

    @Test
    fun `dto should handle empty config`() {
        val dto = PluginDto(
            id = "plugin",
            types = emptyList(),
            name = "Plugin",
            description = "Desc",
            version = "1.0.0",
            author = "Author",
            hasLogo = false,
            state = PluginState.STARTED,
            config = emptyMap(),
            priority = 1,
            trustLevel = PluginTrustLevel.BUNDLED
        )

        assertNotNull(dto.config)
        assertTrue(dto.config.isEmpty())
    }

    @Test
    fun `dto should handle config with null values`() {
        val config = mapOf("key1" to "value1", "key2" to null)
        val dto = PluginDto(
            id = "plugin",
            types = emptyList(),
            name = "Plugin",
            description = "Desc",
            version = "1.0.0",
            author = "Author",
            hasLogo = false,
            state = PluginState.STARTED,
            config = config,
            priority = 1,
            trustLevel = PluginTrustLevel.BUNDLED
        )

        assertEquals("value1", dto.config!!["key1"])
        assertNull(dto.config["key2"])
    }

    @Test
    fun `dto should handle different priority values`() {
        listOf(0, 1, 10, 100, -1).forEach { priority ->
            val dto = PluginDto(
                id = "plugin",
                types = emptyList(),
                name = "Plugin",
                description = "Desc",
                version = "1.0.0",
                author = "Author",
                hasLogo = false,
                state = PluginState.STARTED,
                priority = priority,
                trustLevel = PluginTrustLevel.BUNDLED
            )

            assertEquals(priority, dto.priority)
        }
    }
}

