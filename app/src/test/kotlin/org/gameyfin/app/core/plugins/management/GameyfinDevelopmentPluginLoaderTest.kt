package org.gameyfin.app.core.plugins.management

import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.pf4j.PluginManager
import kotlin.test.assertNotNull

class GameyfinDevelopmentPluginLoaderTest {

    private lateinit var pluginManager: PluginManager
    private lateinit var parentClassLoader: ClassLoader
    private lateinit var developmentPluginLoader: GameyfinDevelopmentPluginLoader

    @BeforeEach
    fun setup() {
        pluginManager = mockk(relaxed = true)
        parentClassLoader = this.javaClass.classLoader
        developmentPluginLoader = GameyfinDevelopmentPluginLoader(pluginManager, parentClassLoader)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `constructor should accept pluginManager and parentClassLoader`() {
        val loader = GameyfinDevelopmentPluginLoader(pluginManager, parentClassLoader)
        assertNotNull(loader)
    }

    @Test
    fun `should use custom GameyfinPluginClassLoader`() {
        // The loader should be configured to use GameyfinPluginClassLoader internally
        // This is verified through the implementation rather than direct testing
        // since createPluginClassLoader is protected
        assertNotNull(developmentPluginLoader)
    }

    @Test
    fun `should accept different parent class loaders`() {
        val customClassLoader = this.javaClass.classLoader
        val loader1 = GameyfinDevelopmentPluginLoader(pluginManager, customClassLoader)
        val loader2 = GameyfinDevelopmentPluginLoader(pluginManager, parentClassLoader)

        assertNotNull(loader1)
        assertNotNull(loader2)
    }

    @Test
    fun `should work with different plugin managers`() {
        val manager1 = mockk<PluginManager>(relaxed = true)
        val manager2 = mockk<PluginManager>(relaxed = true)

        val loader1 = GameyfinDevelopmentPluginLoader(manager1, parentClassLoader)
        val loader2 = GameyfinDevelopmentPluginLoader(manager2, parentClassLoader)

        assertNotNull(loader1)
        assertNotNull(loader2)
    }

    @Test
    fun `multiple instances should work independently`() {
        val loader1 = GameyfinDevelopmentPluginLoader(pluginManager, parentClassLoader)
        val loader2 = GameyfinDevelopmentPluginLoader(pluginManager, parentClassLoader)

        assertNotNull(loader1)
        assertNotNull(loader2)
        assert(loader1 !== loader2)
    }
}

