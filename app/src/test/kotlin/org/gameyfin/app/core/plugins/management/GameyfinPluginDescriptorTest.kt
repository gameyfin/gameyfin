package org.gameyfin.app.core.plugins.management

import org.junit.jupiter.api.Test
import org.pf4j.DefaultPluginDescriptor
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameyfinPluginDescriptorTest {

    @Test
    fun `constructor should initialize all properties correctly`() {
        val descriptor = GameyfinPluginDescriptor(
            pluginUrl = "https://example.com",
            pluginName = "Test Plugin",
            pluginShortDescription = "Short description",
            author = "Test Author"
        )

        assertEquals("https://example.com", descriptor.pluginUrl)
        assertEquals("Test Plugin", descriptor.pluginName)
        assertEquals("Short description", descriptor.pluginShortDescription)
        assertEquals("Test Author", descriptor.author)
    }

    @Test
    fun `constructor with PluginDescriptor should copy all fields`() {
        // Create a GameyfinPluginDescriptor to use as base since we can't set protected fields
        val baseDescriptor = DefaultPluginDescriptor(
            "test-plugin-id",
            "Test Description",
            "org.example.TestPlugin",
            "1.0.0",
            "1.0.0",
            "Test Provider",
            "MIT"
        )

        val descriptor = GameyfinPluginDescriptor(
            descriptor = baseDescriptor,
            url = "https://example.com",
            name = "Test Plugin",
            shortDescription = "Short desc",
            author = "Test Author"
        )

        assertEquals("test-plugin-id", descriptor.pluginId)
        assertEquals("Test Description", descriptor.pluginDescription)
        assertEquals("org.example.TestPlugin", descriptor.pluginClass)
        assertEquals("1.0.0", descriptor.version)
        assertEquals("1.0.0", descriptor.requires)
        assertEquals("MIT", descriptor.license)
        assertEquals("https://example.com", descriptor.pluginUrl)
        assertEquals("Test Plugin", descriptor.pluginName)
        assertEquals("Short desc", descriptor.pluginShortDescription)
        assertEquals("Test Author", descriptor.author)
    }

    @Test
    fun `constructor should handle null url`() {
        val descriptor = GameyfinPluginDescriptor(
            pluginUrl = null,
            pluginName = "Test Plugin",
            pluginShortDescription = "Short description",
            author = "Test Author"
        )

        assertNull(descriptor.pluginUrl)
    }

    @Test
    fun `constructor should handle null shortDescription`() {
        val descriptor = GameyfinPluginDescriptor(
            pluginUrl = "https://example.com",
            pluginName = "Test Plugin",
            pluginShortDescription = null,
            author = "Test Author"
        )

        assertNull(descriptor.pluginShortDescription)
    }

    @Test
    fun `constructor should replace newline indicators with actual newlines in description`() {
        val baseDescriptor = DefaultPluginDescriptor(
            "test-plugin",
            "Line 1<br>Line 2<br>Line 3",
            "org.example.TestPlugin",
            "1.0.0",
            "1.0.0",
            "Author",
            "MIT"
        )

        val descriptor = GameyfinPluginDescriptor(
            descriptor = baseDescriptor,
            url = null,
            name = "Test",
            shortDescription = null,
            author = "Author"
        )

        assertEquals("Line 1\nLine 2\nLine 3", descriptor.pluginDescription)
    }

    @Test
    fun `constructor should handle multiple newline indicators`() {
        val baseDescriptor = DefaultPluginDescriptor(
            "test-plugin",
            "Line 1<br><br>Line 2<br>Line 3",
            "org.example.TestPlugin",
            "1.0.0",
            "1.0.0",
            "Author",
            "MIT"
        )

        val descriptor = GameyfinPluginDescriptor(
            descriptor = baseDescriptor,
            url = null,
            name = "Test",
            shortDescription = null,
            author = "Author"
        )

        assertEquals("Line 1\n\nLine 2\nLine 3", descriptor.pluginDescription)
    }

    @Test
    fun `constructor should handle description without newline indicators`() {
        val baseDescriptor = DefaultPluginDescriptor(
            "test-plugin",
            "Simple description",
            "org.example.TestPlugin",
            "1.0.0",
            "1.0.0",
            "Author",
            "MIT"
        )

        val descriptor = GameyfinPluginDescriptor(
            descriptor = baseDescriptor,
            url = null,
            name = "Test",
            shortDescription = null,
            author = "Author"
        )

        assertEquals("Simple description", descriptor.pluginDescription)
    }

    @Test
    fun `getProvider should return author`() {
        val descriptor = GameyfinPluginDescriptor(
            pluginUrl = "https://example.com",
            pluginName = "Test Plugin",
            pluginShortDescription = "Short description",
            author = "Test Author"
        )

        assertEquals("Test Author", descriptor.provider)
    }

    @Test
    fun `should handle dependencies from base descriptor`() {
        val baseDescriptor = DefaultPluginDescriptor(
            "test-plugin",
            "Test",
            "org.example.TestPlugin",
            "1.0.0",
            "1.0.0",
            "Author",
            "MIT"
        )

        val descriptor = GameyfinPluginDescriptor(
            descriptor = baseDescriptor,
            url = null,
            name = "Test",
            shortDescription = null,
            author = "Author"
        )

        assertNotNull(descriptor.dependencies)
    }

    @Test
    fun `data class should support equality`() {
        val descriptor1 = GameyfinPluginDescriptor(
            pluginUrl = "https://example.com",
            pluginName = "Test Plugin",
            pluginShortDescription = "Short description",
            author = "Test Author"
        )

        val descriptor2 = GameyfinPluginDescriptor(
            pluginUrl = "https://example.com",
            pluginName = "Test Plugin",
            pluginShortDescription = "Short description",
            author = "Test Author"
        )

        assertEquals(descriptor1, descriptor2)
    }

    @Test
    fun `data class should support copy`() {
        val descriptor1 = GameyfinPluginDescriptor(
            pluginUrl = "https://example.com",
            pluginName = "Test Plugin",
            pluginShortDescription = "Short description",
            author = "Test Author"
        )

        val descriptor2 = descriptor1.copy(pluginName = "Modified Plugin")

        assertEquals("https://example.com", descriptor2.pluginUrl)
        assertEquals("Modified Plugin", descriptor2.pluginName)
        assertEquals("Short description", descriptor2.pluginShortDescription)
        assertEquals("Test Author", descriptor2.author)
    }

    @Test
    fun `should handle special characters in fields`() {
        val descriptor = GameyfinPluginDescriptor(
            pluginUrl = "https://example.com/plugin?v=1&t=2",
            pluginName = "Test Plugin™",
            pluginShortDescription = "Short & Sweet",
            author = "Test Author <test@example.com>"
        )

        assertEquals("https://example.com/plugin?v=1&t=2", descriptor.pluginUrl)
        assertEquals("Test Plugin™", descriptor.pluginName)
        assertEquals("Short & Sweet", descriptor.pluginShortDescription)
        assertEquals("Test Author <test@example.com>", descriptor.author)
    }

    @Test
    fun `should preserve all fields from base descriptor including license`() {
        val baseDescriptor = DefaultPluginDescriptor(
            "licensed-plugin",
            "Description",
            "org.example.TestPlugin",
            "1.0.0",
            "1.0.0",
            "Author",
            "Apache-2.0"
        )

        val descriptor = GameyfinPluginDescriptor(
            descriptor = baseDescriptor,
            url = "https://example.com",
            name = "Licensed Plugin",
            shortDescription = "Short",
            author = "Author"
        )

        assertEquals("Apache-2.0", descriptor.license)
    }

    @Test
    fun `NEWLINE_INDICATOR constant should have correct value`() {
        assertEquals("<br>", GameyfinPluginDescriptor.NEWLINE_INDICATOR)
    }
}

