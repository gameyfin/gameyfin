package org.gameyfin.app.core.plugins.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PluginConfigEntryTest {

    @Test
    fun `PluginConfigEntry should store all properties correctly`() {
        val key = PluginConfigEntryKey("test-plugin", "api-key")
        val entry = PluginConfigEntry(key, "secret-value")

        assertEquals(key, entry.id)
        assertEquals("secret-value", entry.value)
    }

    @Test
    fun `PluginConfigEntry should handle empty value`() {
        val key = PluginConfigEntryKey("test-plugin", "empty-key")
        val entry = PluginConfigEntry(key, "")

        assertEquals("", entry.value)
    }

    @Test
    fun `PluginConfigEntry should handle special characters in value`() {
        val key = PluginConfigEntryKey("test-plugin", "special-key")
        val specialValue = "value-with-!@#$%^&*()_+-=[]{}|;:',.<>?/\\"
        val entry = PluginConfigEntry(key, specialValue)

        assertEquals(specialValue, entry.value)
    }

    @Test
    fun `PluginConfigEntry equality should be based on id and value`() {
        val key1 = PluginConfigEntryKey("plugin1", "key1")
        val key2 = PluginConfigEntryKey("plugin1", "key1")
        val entry1 = PluginConfigEntry(key1, "value1")
        val entry2 = PluginConfigEntry(key2, "value1")

        assertEquals(entry1, entry2)
    }

    @Test
    fun `PluginConfigEntry should be different with different values`() {
        val key = PluginConfigEntryKey("plugin1", "key1")
        val entry1 = PluginConfigEntry(key, "value1")
        val entry2 = PluginConfigEntry(key, "value2")

        assert(entry1 != entry2)
    }

    @Test
    fun `PluginConfigEntryKey should store pluginId and key correctly`() {
        val key = PluginConfigEntryKey("my-plugin", "my-key")

        assertEquals("my-plugin", key.pluginId)
        assertEquals("my-key", key.key)
    }

    @Test
    fun `PluginConfigEntryKey should handle special characters in pluginId`() {
        val key = PluginConfigEntryKey("org.example.plugin-v1", "api-key")

        assertEquals("org.example.plugin-v1", key.pluginId)
    }

    @Test
    fun `PluginConfigEntryKey should handle special characters in key`() {
        val key = PluginConfigEntryKey("plugin", "api.key.nested")

        assertEquals("api.key.nested", key.key)
    }

    @Test
    fun `PluginConfigEntryKey equality should be based on pluginId and key`() {
        val key1 = PluginConfigEntryKey("plugin1", "key1")
        val key2 = PluginConfigEntryKey("plugin1", "key1")

        assertEquals(key1, key2)
    }

    @Test
    fun `PluginConfigEntryKey should be different with different pluginId`() {
        val key1 = PluginConfigEntryKey("plugin1", "key1")
        val key2 = PluginConfigEntryKey("plugin2", "key1")

        assert(key1 != key2)
    }

    @Test
    fun `PluginConfigEntryKey should be different with different key`() {
        val key1 = PluginConfigEntryKey("plugin1", "key1")
        val key2 = PluginConfigEntryKey("plugin1", "key2")

        assert(key1 != key2)
    }

    @Test
    fun `PluginConfigEntryKey should have consistent hashCode for equal keys`() {
        val key1 = PluginConfigEntryKey("plugin1", "key1")
        val key2 = PluginConfigEntryKey("plugin1", "key1")

        assertEquals(key1.hashCode(), key2.hashCode())
    }

    @Test
    fun `PluginConfigEntry should work as data class with copy`() {
        val key = PluginConfigEntryKey("plugin1", "key1")
        val entry1 = PluginConfigEntry(key, "value1")
        val entry2 = entry1.copy(value = "value2")

        assertEquals(key, entry2.id)
        assertEquals("value2", entry2.value)
    }

    @Test
    fun `PluginConfigEntryKey should work as data class with copy`() {
        val key1 = PluginConfigEntryKey("plugin1", "key1")
        val key2 = key1.copy(key = "key2")

        assertEquals("plugin1", key2.pluginId)
        assertEquals("key2", key2.key)
    }

    @Test
    fun `PluginConfigEntry should handle multiline values`() {
        val key = PluginConfigEntryKey("plugin", "description")
        val multilineValue = """
            Line 1
            Line 2
            Line 3
        """.trimIndent()
        val entry = PluginConfigEntry(key, multilineValue)

        assertEquals(multilineValue, entry.value)
    }

    @Test
    fun `PluginConfigEntry should handle unicode characters`() {
        val key = PluginConfigEntryKey("plugin", "unicode-key")
        val unicodeValue = "Hello 世界 🌍"
        val entry = PluginConfigEntry(key, unicodeValue)

        assertEquals(unicodeValue, entry.value)
    }
}

