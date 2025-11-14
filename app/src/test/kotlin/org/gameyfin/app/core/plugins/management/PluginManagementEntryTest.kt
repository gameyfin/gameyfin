package org.gameyfin.app.core.plugins.management

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginManagementEntryTest {

    @Test
    fun `PluginManagementEntry should store all properties correctly`() {
        val entry = PluginManagementEntry(
            pluginId = "test-plugin",
            enabled = true,
            priority = 10,
            trustLevel = PluginTrustLevel.OFFICIAL
        )

        assertEquals("test-plugin", entry.pluginId)
        assertTrue(entry.enabled)
        assertEquals(10, entry.priority)
        assertEquals(PluginTrustLevel.OFFICIAL, entry.trustLevel)
    }

    @Test
    fun `PluginManagementEntry should have correct default values`() {
        val entry = PluginManagementEntry("test-plugin")

        assertEquals("test-plugin", entry.pluginId)
        assertFalse(entry.enabled)
        assertEquals(0, entry.priority)
        assertEquals(PluginTrustLevel.UNKNOWN, entry.trustLevel)
    }

    @Test
    fun `PluginManagementEntry should be mutable`() {
        val entry = PluginManagementEntry("test-plugin")

        entry.enabled = true
        entry.priority = 5
        entry.trustLevel = PluginTrustLevel.BUNDLED

        assertTrue(entry.enabled)
        assertEquals(5, entry.priority)
        assertEquals(PluginTrustLevel.BUNDLED, entry.trustLevel)
    }

    @Test
    fun `PluginManagementEntry equality should be based on all fields`() {
        val entry1 = PluginManagementEntry("plugin1", true, 1, PluginTrustLevel.OFFICIAL)
        val entry2 = PluginManagementEntry("plugin1", true, 1, PluginTrustLevel.OFFICIAL)

        assertEquals(entry1, entry2)
    }

    @Test
    fun `PluginManagementEntry should be different with different pluginId`() {
        val entry1 = PluginManagementEntry("plugin1", true, 1)
        val entry2 = PluginManagementEntry("plugin2", true, 1)

        assert(entry1 != entry2)
    }

    @Test
    fun `PluginManagementEntry should be different with different enabled state`() {
        val entry1 = PluginManagementEntry("plugin1", true, 1)
        val entry2 = PluginManagementEntry("plugin1", false, 1)

        assert(entry1 != entry2)
    }

    @Test
    fun `PluginManagementEntry should be different with different priority`() {
        val entry1 = PluginManagementEntry("plugin1", true, 1)
        val entry2 = PluginManagementEntry("plugin1", true, 2)

        assert(entry1 != entry2)
    }

    @Test
    fun `PluginManagementEntry should be different with different trustLevel`() {
        val entry1 = PluginManagementEntry("plugin1", true, 1, PluginTrustLevel.OFFICIAL)
        val entry2 = PluginManagementEntry("plugin1", true, 1, PluginTrustLevel.THIRD_PARTY)

        assert(entry1 != entry2)
    }

    @Test
    fun `PluginManagementEntry should work as data class with copy`() {
        val entry1 = PluginManagementEntry("plugin1", true, 1, PluginTrustLevel.OFFICIAL)
        val entry2 = entry1.copy(enabled = false)

        assertEquals("plugin1", entry2.pluginId)
        assertFalse(entry2.enabled)
        assertEquals(1, entry2.priority)
        assertEquals(PluginTrustLevel.OFFICIAL, entry2.trustLevel)
    }

    @Test
    fun `PluginManagementEntry should handle all trust levels`() {
        val trustLevels = listOf(
            PluginTrustLevel.BUNDLED,
            PluginTrustLevel.OFFICIAL,
            PluginTrustLevel.THIRD_PARTY,
            PluginTrustLevel.UNTRUSTED,
            PluginTrustLevel.UNKNOWN
        )

        trustLevels.forEach { trustLevel ->
            val entry = PluginManagementEntry("plugin", trustLevel = trustLevel)
            assertEquals(trustLevel, entry.trustLevel)
        }
    }

    @Test
    fun `PluginManagementEntry should handle negative priority`() {
        val entry = PluginManagementEntry("plugin", priority = -1)
        assertEquals(-1, entry.priority)
    }

    @Test
    fun `PluginManagementEntry should handle large priority values`() {
        val entry = PluginManagementEntry("plugin", priority = Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, entry.priority)
    }

    @Test
    fun `PluginManagementEntry should handle special characters in pluginId`() {
        val entry = PluginManagementEntry("org.example.plugin-v1.0")
        assertEquals("org.example.plugin-v1.0", entry.pluginId)
    }

    @Test
    fun `PluginManagementEntry should have consistent hashCode for equal entries`() {
        val entry1 = PluginManagementEntry("plugin1", true, 1, PluginTrustLevel.OFFICIAL)
        val entry2 = PluginManagementEntry("plugin1", true, 1, PluginTrustLevel.OFFICIAL)

        assertEquals(entry1.hashCode(), entry2.hashCode())
    }
}

