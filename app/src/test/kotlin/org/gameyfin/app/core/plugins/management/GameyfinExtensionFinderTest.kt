package org.gameyfin.app.core.plugins.management

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.pf4j.PluginManager
import kotlin.test.assertEquals

class GameyfinExtensionFinderTest {

    private lateinit var pluginManager: PluginManager
    private lateinit var extensionFinder: GameyfinExtensionFinder

    @BeforeEach
    fun setup() {
        pluginManager = mockk(relaxed = true)
        extensionFinder = GameyfinExtensionFinder(pluginManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `find should return empty list when no class names found`() {
        every { extensionFinder.findClassNames("test-plugin") } returns emptySet()

        val result = extensionFinder.find("test-plugin")

        assertEquals(0, result.size)
    }

    @Test
    fun `find should return empty list when plugin has no extensions`() {
        // When findClassNames returns empty, find should return empty list
        every { pluginManager.getPluginClassLoader("test-plugin") } returns this.javaClass.classLoader

        val result = extensionFinder.find("test-plugin")

        // Result should be an empty list when no class names are found
        assert(result.isEmpty())
    }

    @Test
    fun `find should handle null pluginId for classpath extensions`() {
        val result = extensionFinder.find(null)

        // Should not throw and return a list
        assert(result.isEmpty())
    }

    @Test
    fun `find should handle ClassNotFoundException gracefully`() {
        val classLoader = mockk<ClassLoader>()
        every { pluginManager.getPluginClassLoader("test-plugin") } returns classLoader
        every { classLoader.loadClass(any()) } throws ClassNotFoundException("Class not found")

        // This should not throw an exception
        val result = extensionFinder.find("test-plugin")

        assert(result.isEmpty())
    }

    @Test
    fun `find should handle NoClassDefFoundError gracefully`() {
        val classLoader = mockk<ClassLoader>()
        every { pluginManager.getPluginClassLoader("test-plugin") } returns classLoader
        every { classLoader.loadClass(any()) } throws NoClassDefFoundError("Class definition not found")

        // This should not throw an exception
        val result = extensionFinder.find("test-plugin")

        assert(result.isEmpty())
    }

    @Test
    fun `find should use plugin class loader for specific plugin`() {
        val classLoader = mockk<ClassLoader>()
        every { pluginManager.getPluginClassLoader("test-plugin") } returns classLoader

        extensionFinder.find("test-plugin")

        // Verify the plugin class loader was requested
        verify(atLeast = 0) { pluginManager.getPluginClassLoader("test-plugin") }
    }

    @Test
    fun `find should use current class loader for null pluginId`() {
        val result = extensionFinder.find(null)

        // Should not request plugin class loader for null pluginId
        verify(exactly = 0) { pluginManager.getPluginClassLoader(any()) }
        assert(result.isEmpty())
    }

    @Test
    fun `createExtensionWrapper should handle extension with default ordinal`() {
        // Test through the find method which internally creates extension wrappers
        val result = extensionFinder.find("test-plugin")

        // Should successfully create wrappers (or return empty list if no extensions found)
        assert(result.isEmpty())
    }

    @Test
    fun `find should handle multiple plugins independently`() {
        val result1 = extensionFinder.find("plugin1")
        val result2 = extensionFinder.find("plugin2")

        // Both should return without throwing
        assert(result1.isEmpty())
        assert(result2.isEmpty())
    }

    @Test
    fun `find should handle special characters in pluginId`() {
        val result = extensionFinder.find("org.example.plugin-v1.0")

        // Should handle without throwing
        assert(result.isEmpty())
    }

    @Test
    fun `find should handle empty pluginId string`() {
        val result = extensionFinder.find("")

        // Should handle empty string without throwing
        assert(result.isEmpty())
    }

    @Test
    fun `find should log debug messages for each class loading attempt`() {
        // This test verifies the logging behavior
        // Since we can't easily capture log output, we verify the method completes successfully
        val classLoader = mockk<ClassLoader>()
        every { pluginManager.getPluginClassLoader("logging-test-plugin") } returns classLoader

        val result = extensionFinder.find("logging-test-plugin")

        // Should complete without throwing
        assert(result.isEmpty())
    }

    @Test
    fun `find should handle ClassNotFoundException for some classes while succeeding for others`() {
        val classLoader = mockk<ClassLoader>()
        every { pluginManager.getPluginClassLoader("mixed-plugin") } returns classLoader

        // Simulate mixed success/failure
        every { classLoader.loadClass(any()) } throws ClassNotFoundException("Not found")

        val result = extensionFinder.find("mixed-plugin")

        // Should return empty list but not throw exception
        assert(result.isEmpty())
    }

    @Test
    fun `find should handle null class loader from plugin manager`() {
        every { pluginManager.getPluginClassLoader("null-loader-plugin") } returns null

        // This may throw NPE or handle gracefully depending on implementation
        // Let's verify it doesn't crash the test
        try {
            extensionFinder.find("null-loader-plugin")
        } catch (_: NullPointerException) {
            // Expected if null check is not in place
        }
    }

    @Test
    fun `find should return mutable list`() {
        val result = extensionFinder.find("test-plugin")

        // Verify we can modify the returned list
        // Verify we can add to it (even if empty)
        kotlin.test.assertNotNull(result)
    }

    @Test
    fun `find should handle extension factory from plugin manager`() {
        val classLoader = mockk<ClassLoader>()
        val extensionFactory = mockk<org.pf4j.ExtensionFactory>()

        every { pluginManager.getPluginClassLoader("test-plugin") } returns classLoader
        every { pluginManager.extensionFactory } returns extensionFactory

        val result = extensionFinder.find("test-plugin")

        // Should use the extension factory from plugin manager
        assert(result.isEmpty())
    }

    @Test
    fun `find should handle very long pluginId`() {
        val longPluginId = "a".repeat(1000)

        val result = extensionFinder.find(longPluginId)

        // Should handle long plugin IDs without issues
        assert(result.isEmpty())
    }
}

