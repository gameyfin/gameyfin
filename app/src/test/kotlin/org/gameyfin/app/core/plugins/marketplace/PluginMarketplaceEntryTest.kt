package org.gameyfin.app.core.plugins.marketplace

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginMarketplaceEntryTest {

    @Test
    fun `PluginMarketplaceEntry should have correct default values`() {
        val before = Instant.now()

        val entry = PluginMarketplaceEntry(
            id = "custom",
            name = "Custom Marketplace",
            url = "https://github.com/example/plugins",
            indexUrl = "https://raw.githubusercontent.com/example/plugins/main/plugins.json"
        )

        val after = Instant.now()

        assertTrue(entry.enabled)
        assertFalse(entry.official)
        assertFalse(entry.createdAt.isBefore(before))
        assertFalse(entry.createdAt.isAfter(after))
    }

    @Test
    fun `PluginMarketplaceEntry should store all properties correctly`() {
        val createdAt = Instant.parse("2024-01-01T12:00:00Z")

        val entry = PluginMarketplaceEntry(
            id = "official",
            name = "Official Marketplace",
            url = "https://github.com/gameyfin/plugins",
            indexUrl = "https://raw.githubusercontent.com/gameyfin/plugins/main/plugins.json",
            enabled = false,
            official = true,
            createdAt = createdAt
        )

        assertEquals("official", entry.id)
        assertEquals("Official Marketplace", entry.name)
        assertEquals("https://github.com/gameyfin/plugins", entry.url)
        assertEquals("https://raw.githubusercontent.com/gameyfin/plugins/main/plugins.json", entry.indexUrl)
        assertFalse(entry.enabled)
        assertTrue(entry.official)
        assertEquals(createdAt, entry.createdAt)
    }

    @Test
    fun `PluginMarketplaceEntry mutable properties should be updatable`() {
        val entry = PluginMarketplaceEntry(
            id = "custom",
            name = "Custom Marketplace",
            url = "https://github.com/example/plugins",
            indexUrl = "https://raw.githubusercontent.com/example/plugins/main/plugins.json"
        )

        entry.name = "Renamed Marketplace"
        entry.url = "https://github.com/example/other-plugins"
        entry.indexUrl = "https://raw.githubusercontent.com/example/other-plugins/main/plugins.json"
        entry.enabled = false

        assertEquals("custom", entry.id)
        assertEquals("Renamed Marketplace", entry.name)
        assertEquals("https://github.com/example/other-plugins", entry.url)
        assertEquals("https://raw.githubusercontent.com/example/other-plugins/main/plugins.json", entry.indexUrl)
        assertFalse(entry.enabled)
    }
}
