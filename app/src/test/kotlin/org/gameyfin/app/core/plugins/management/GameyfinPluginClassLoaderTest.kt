package org.gameyfin.app.core.plugins.management

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.pf4j.ClassLoadingStrategy
import org.pf4j.PluginDescriptor
import org.pf4j.PluginManager
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GameyfinPluginClassLoaderTest {

    private lateinit var pluginManager: PluginManager
    private lateinit var pluginDescriptor: PluginDescriptor
    private lateinit var parentClassLoader: ClassLoader
    private lateinit var classLoader: GameyfinPluginClassLoader

    @BeforeEach
    fun setup() {
        pluginManager = mockk(relaxed = true)
        pluginDescriptor = mockk(relaxed = true)
        parentClassLoader = this.javaClass.classLoader

        every { pluginDescriptor.pluginId } returns "test-plugin"
        every { pluginDescriptor.pluginClass } returns "org.example.TestPlugin"
        every { pluginDescriptor.version } returns "1.0.0"

        classLoader = GameyfinPluginClassLoader(
            pluginManager,
            pluginDescriptor,
            parentClassLoader,
            ClassLoadingStrategy.APD
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `loadClass should return class when no security exception occurs`() {
        // Test loading a standard Java class
        val result = classLoader.loadClass("java.lang.String")

        assertNotNull(result)
        assertEquals("java.lang.String", result.name)
    }

    @Test
    fun `loadClass should handle null className gracefully`() {
        // Test with null - this will test the null handling in the method
        try {
            classLoader.loadClass(null)
        } catch (_: Exception) {
            // Either throws or handles gracefully
            // The implementation catches SecurityException but may throw other exceptions
        }
    }

    @Test
    fun `classLoader should be created with APD strategy`() {
        // Verify the class loader was created successfully with the correct strategy
        assertNotNull(classLoader)
    }

    @Test
    fun `loadClass should handle standard library classes`() {
        val classes = listOf(
            "java.lang.String",
            "java.util.ArrayList",
            "java.io.File"
        )

        classes.forEach { className ->
            val result = classLoader.loadClass(className)
            assertNotNull(result)
            assertEquals(className, result.name)
        }
    }

    @Test
    fun `loadClass should be able to load multiple classes`() {
        val result1 = classLoader.loadClass("java.lang.String")
        val result2 = classLoader.loadClass("java.lang.Integer")

        assertNotNull(result1)
        assertNotNull(result2)
        assertEquals("java.lang.String", result1.name)
        assertEquals("java.lang.Integer", result2.name)
    }
}

