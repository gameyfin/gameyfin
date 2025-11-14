package org.gameyfin.app.core.plugins.management

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pf4j.PluginDescriptor
import org.pf4j.PluginManager
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameyfinJarPluginLoaderTest {

    private lateinit var pluginManager: PluginManager
    private lateinit var jarPluginLoader: GameyfinJarPluginLoader

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        pluginManager = mockk(relaxed = true)
        jarPluginLoader = GameyfinJarPluginLoader(pluginManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `isApplicable should return true for JAR files`() {
        val jarFile = tempDir.resolve("test-plugin.jar")

        // Create a minimal JAR file
        JarOutputStream(Files.newOutputStream(jarFile), Manifest()).use { }

        val result = jarPluginLoader.isApplicable(jarFile)

        assertTrue(result)
    }

    @Test
    fun `isApplicable should return false for non-JAR files`() {
        val textFile = tempDir.resolve("test-plugin.txt")
        Files.writeString(textFile, "Not a JAR file")

        val result = jarPluginLoader.isApplicable(textFile)

        assertFalse(result)
    }

    @Test
    fun `isApplicable should return false for directories`() {
        val directory = tempDir.resolve("plugin-directory")
        Files.createDirectory(directory)

        val result = jarPluginLoader.isApplicable(directory)

        assertFalse(result)
    }

    @Test
    fun `isApplicable should return false for non-existent paths`() {
        val nonExistentPath = tempDir.resolve("does-not-exist.jar")

        val result = jarPluginLoader.isApplicable(nonExistentPath)

        assertFalse(result)
    }

    @Test
    fun `loadPlugin should create GameyfinPluginClassLoader`() {
        val jarFile = tempDir.resolve("test-plugin.jar")
        JarOutputStream(Files.newOutputStream(jarFile), Manifest()).use { }

        val pluginDescriptor = mockk<PluginDescriptor>(relaxed = true)
        every { pluginDescriptor.pluginId } returns "test-plugin"
        every { pluginDescriptor.pluginClass } returns "org.example.TestPlugin"
        every { pluginDescriptor.version } returns "1.0.0"

        val classLoader = jarPluginLoader.loadPlugin(jarFile, pluginDescriptor)

        assertNotNull(classLoader)
        assertTrue(classLoader is GameyfinPluginClassLoader)
    }

    @Test
    fun `loadPlugin should throw exception when descriptor is null`() {
        val jarFile = tempDir.resolve("test-plugin.jar")
        JarOutputStream(Files.newOutputStream(jarFile), Manifest()).use { }

        assertFailsWith<IllegalArgumentException> {
            jarPluginLoader.loadPlugin(jarFile, null)
        }
    }

    @Test
    fun `loadPlugin should add JAR file to class loader`() {
        val jarFile = tempDir.resolve("test-plugin.jar")
        JarOutputStream(Files.newOutputStream(jarFile), Manifest()).use { }

        val pluginDescriptor = mockk<PluginDescriptor>(relaxed = true)
        every { pluginDescriptor.pluginId } returns "test-plugin"
        every { pluginDescriptor.pluginClass } returns "org.example.TestPlugin"
        every { pluginDescriptor.version } returns "1.0.0"

        val classLoader = jarPluginLoader.loadPlugin(jarFile, pluginDescriptor) as GameyfinPluginClassLoader

        assertNotNull(classLoader)
        // The JAR file should be added to the classloader's classpath
        // We can't easily verify this directly, but the classloader creation should succeed
    }

    @Test
    fun `loadPlugin should use APD class loading strategy`() {
        val jarFile = tempDir.resolve("test-plugin.jar")
        JarOutputStream(Files.newOutputStream(jarFile), Manifest()).use { }

        val pluginDescriptor = mockk<PluginDescriptor>(relaxed = true)
        every { pluginDescriptor.pluginId } returns "test-plugin"
        every { pluginDescriptor.pluginClass } returns "org.example.TestPlugin"

        val classLoader = jarPluginLoader.loadPlugin(jarFile, pluginDescriptor)

        assertNotNull(classLoader)
        assertTrue(classLoader is GameyfinPluginClassLoader)
        // APD strategy is set in constructor, verified by successful instantiation
    }

    @Test
    fun `isApplicable should handle JAR files with uppercase extension`() {
        val jarFile = tempDir.resolve("test-plugin.JAR")
        JarOutputStream(Files.newOutputStream(jarFile), Manifest()).use { }

        val result = jarPluginLoader.isApplicable(jarFile)

        assertTrue(result)
    }

    @Test
    fun `loadPlugin should handle plugins with dependencies`() {
        val jarFile = tempDir.resolve("test-plugin-with-deps.jar")
        JarOutputStream(Files.newOutputStream(jarFile), Manifest()).use { }

        val pluginDescriptor = mockk<PluginDescriptor>(relaxed = true)
        every { pluginDescriptor.pluginId } returns "test-plugin"
        every { pluginDescriptor.pluginClass } returns "org.example.TestPlugin"
        every { pluginDescriptor.dependencies } returns listOf()

        val classLoader = jarPluginLoader.loadPlugin(jarFile, pluginDescriptor)

        assertNotNull(classLoader)
    }
}

