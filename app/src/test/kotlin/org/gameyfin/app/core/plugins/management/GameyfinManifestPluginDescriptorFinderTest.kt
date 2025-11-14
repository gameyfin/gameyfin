package org.gameyfin.app.core.plugins.management

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.jar.Attributes
import java.util.jar.Manifest
import kotlin.test.*

class GameyfinManifestPluginDescriptorFinderTest {

    private lateinit var descriptorFinder: GameyfinManifestPluginDescriptorFinder

    @BeforeEach
    fun setup() {
        descriptorFinder = GameyfinManifestPluginDescriptorFinder()
    }

    @AfterEach
    fun tearDown() {
        // Cleanup if needed
    }

    @Test
    fun `createPluginDescriptor should create descriptor with all required fields`() {
        val manifest = createManifest(
            pluginId = "test-plugin",
            pluginVersion = "1.0.0",
            pluginClass = "org.example.TestPlugin",
            pluginName = "Test Plugin",
            pluginAuthor = "Test Author",
            pluginDescription = "Test Description",
            pluginShortDescription = "Short Description",
            pluginUrl = "https://example.com/plugin"
        )

        val descriptor = descriptorFinder.createPluginDescriptor(manifest)

        assertNotNull(descriptor)
        assertEquals("test-plugin", descriptor.pluginId)
        assertEquals("1.0.0", descriptor.version)
        assertEquals("org.example.TestPlugin", descriptor.pluginClass)
        assertEquals("Test Plugin", descriptor.pluginName)
        assertEquals("Test Author", descriptor.author)
        assertEquals("Test Description", descriptor.pluginDescription)
        assertEquals("Short Description", descriptor.pluginShortDescription)
        assertEquals("https://example.com/plugin", descriptor.pluginUrl)
    }

    @Test
    fun `createPluginDescriptor should throw exception when manifest is null`() {
        assertFailsWith<IllegalArgumentException> {
            descriptorFinder.createPluginDescriptor(null)
        }
    }

    @Test
    fun `createPluginDescriptor should throw exception when Plugin-Name is missing`() {
        val manifest = createManifest(
            pluginId = "test-plugin",
            pluginVersion = "1.0.0",
            pluginClass = "org.example.TestPlugin",
            pluginAuthor = "Test Author"
            // Plugin-Name is missing
        )

        assertFailsWith<IllegalStateException> {
            descriptorFinder.createPluginDescriptor(manifest)
        }
    }

    @Test
    fun `createPluginDescriptor should throw exception when Plugin-Author is missing`() {
        val manifest = createManifest(
            pluginId = "test-plugin",
            pluginVersion = "1.0.0",
            pluginClass = "org.example.TestPlugin",
            pluginName = "Test Plugin"
            // Plugin-Author is missing
        )

        assertFailsWith<IllegalStateException> {
            descriptorFinder.createPluginDescriptor(manifest)
        }
    }

    @Test
    fun `createPluginDescriptor should handle missing Plugin-Short-Description`() {
        val manifest = createManifest(
            pluginId = "test-plugin",
            pluginVersion = "1.0.0",
            pluginClass = "org.example.TestPlugin",
            pluginName = "Test Plugin",
            pluginAuthor = "Test Author"
            // Plugin-Short-Description is optional
        )

        val descriptor = descriptorFinder.createPluginDescriptor(manifest)

        assertNotNull(descriptor)
        assertNull(descriptor.pluginShortDescription)
    }

    @Test
    fun `createPluginDescriptor should handle missing Plugin-Url`() {
        val manifest = createManifest(
            pluginId = "test-plugin",
            pluginVersion = "1.0.0",
            pluginClass = "org.example.TestPlugin",
            pluginName = "Test Plugin",
            pluginAuthor = "Test Author"
            // Plugin-Url is optional
        )

        val descriptor = descriptorFinder.createPluginDescriptor(manifest)

        assertNotNull(descriptor)
        assertNull(descriptor.pluginUrl)
    }

    @Test
    fun `createPluginDescriptor should replace newline indicators in description`() {
        val manifest = createManifest(
            pluginId = "test-plugin",
            pluginVersion = "1.0.0",
            pluginClass = "org.example.TestPlugin",
            pluginName = "Test Plugin",
            pluginAuthor = "Test Author",
            pluginDescription = "Line 1<br>Line 2<br>Line 3"
        )

        val descriptor = descriptorFinder.createPluginDescriptor(manifest)

        assertNotNull(descriptor)
        assertEquals("Line 1\nLine 2\nLine 3", descriptor.pluginDescription)
    }

    @Test
    fun `createPluginDescriptor should handle description without newline indicators`() {
        val manifest = createManifest(
            pluginId = "test-plugin",
            pluginVersion = "1.0.0",
            pluginClass = "org.example.TestPlugin",
            pluginName = "Test Plugin",
            pluginAuthor = "Test Author",
            pluginDescription = "Simple description without line breaks"
        )

        val descriptor = descriptorFinder.createPluginDescriptor(manifest)

        assertNotNull(descriptor)
        assertEquals("Simple description without line breaks", descriptor.pluginDescription)
    }

    @Test
    fun `createPluginDescriptor should set provider to author`() {
        val manifest = createManifest(
            pluginId = "test-plugin",
            pluginVersion = "1.0.0",
            pluginClass = "org.example.TestPlugin",
            pluginName = "Test Plugin",
            pluginAuthor = "John Doe"
        )

        val descriptor = descriptorFinder.createPluginDescriptor(manifest)

        assertNotNull(descriptor)
        assertEquals("John Doe", descriptor.provider)
        assertEquals("John Doe", descriptor.author)
    }

    @Test
    fun `createPluginDescriptor should handle plugins with dependencies`() {
        val manifest = createManifest(
            pluginId = "test-plugin",
            pluginVersion = "1.0.0",
            pluginClass = "org.example.TestPlugin",
            pluginName = "Test Plugin",
            pluginAuthor = "Test Author",
            pluginDependencies = "dependency1, dependency2"
        )

        val descriptor = descriptorFinder.createPluginDescriptor(manifest)

        assertNotNull(descriptor)
        assertEquals(2, descriptor.dependencies.size)
    }

    @Test
    fun `createPluginDescriptor should handle plugins with requires field`() {
        val manifest = createManifest(
            pluginId = "test-plugin",
            pluginVersion = "1.0.0",
            pluginClass = "org.example.TestPlugin",
            pluginName = "Test Plugin",
            pluginAuthor = "Test Author",
            pluginRequires = "2.0.0"
        )

        val descriptor = descriptorFinder.createPluginDescriptor(manifest)

        assertNotNull(descriptor)
        assertEquals("2.0.0", descriptor.requires)
    }

    @Test
    fun `createPluginDescriptor should handle plugins with license`() {
        val manifest = createManifest(
            pluginId = "test-plugin",
            pluginVersion = "1.0.0",
            pluginClass = "org.example.TestPlugin",
            pluginName = "Test Plugin",
            pluginAuthor = "Test Author",
            pluginLicense = "MIT"
        )

        val descriptor = descriptorFinder.createPluginDescriptor(manifest)

        assertNotNull(descriptor)
        assertEquals("MIT", descriptor.license)
    }

    @Test
    fun `createPluginDescriptor should handle complex plugin metadata`() {
        val manifest = createManifest(
            pluginId = "complex-plugin",
            pluginVersion = "2.5.1",
            pluginClass = "com.example.complex.ComplexPlugin",
            pluginName = "Complex Plugin",
            pluginAuthor = "Complex Author",
            pluginDescription = "Multi-line<br>Description<br>With special chars: @#$%",
            pluginShortDescription = "Complex plugin with many features",
            pluginUrl = "https://github.com/example/complex-plugin",
            pluginDependencies = "dep1@1.0.0, dep2@2.0.0",
            pluginRequires = "3.0.0",
            pluginLicense = "Apache-2.0"
        )

        val descriptor = descriptorFinder.createPluginDescriptor(manifest)

        assertNotNull(descriptor)
        assertEquals("complex-plugin", descriptor.pluginId)
        assertEquals("2.5.1", descriptor.version)
        assertEquals("com.example.complex.ComplexPlugin", descriptor.pluginClass)
        assertEquals("Complex Plugin", descriptor.pluginName)
        assertEquals("Complex Author", descriptor.author)
        assertTrue(descriptor.pluginDescription.contains("\n"))
        assertEquals("Complex plugin with many features", descriptor.pluginShortDescription)
        assertEquals("https://github.com/example/complex-plugin", descriptor.pluginUrl)
        assertEquals("3.0.0", descriptor.requires)
        assertEquals("Apache-2.0", descriptor.license)
    }

    private fun createManifest(
        pluginId: String? = null,
        pluginVersion: String? = null,
        pluginClass: String? = null,
        pluginName: String? = null,
        pluginAuthor: String? = null,
        pluginDescription: String? = null,
        pluginShortDescription: String? = null,
        pluginUrl: String? = null,
        pluginDependencies: String? = null,
        pluginRequires: String? = null,
        pluginLicense: String? = null
    ): Manifest {
        val manifest = Manifest()
        val attributes = manifest.mainAttributes
        attributes[Attributes.Name.MANIFEST_VERSION] = "1.0"

        pluginId?.let { attributes.putValue("Plugin-Id", it) }
        pluginVersion?.let { attributes.putValue("Plugin-Version", it) }
        pluginClass?.let { attributes.putValue("Plugin-Class", it) }
        pluginName?.let { attributes.putValue("Plugin-Name", it) }
        pluginAuthor?.let { attributes.putValue("Plugin-Author", it) }
        pluginDescription?.let { attributes.putValue("Plugin-Description", it) }
        pluginShortDescription?.let { attributes.putValue("Plugin-Short-Description", it) }
        pluginUrl?.let { attributes.putValue("Plugin-Url", it) }
        pluginDependencies?.let { attributes.putValue("Plugin-Dependencies", it) }
        pluginRequires?.let { attributes.putValue("Plugin-Requires", it) }
        pluginLicense?.let { attributes.putValue("Plugin-License", it) }

        return manifest
    }
}

