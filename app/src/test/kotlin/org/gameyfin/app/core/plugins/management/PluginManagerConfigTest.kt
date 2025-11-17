package org.gameyfin.app.core.plugins.management

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class PluginManagerConfigTest {

    private lateinit var pluginManager: GameyfinPluginManager
    private lateinit var pluginManagerConfig: PluginManagerConfig

    @BeforeEach
    fun setup() {
        pluginManager = mockk(relaxed = true)
        pluginManagerConfig = PluginManagerConfig(pluginManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `loadPlugins should call loadPlugins on pluginManager`() {
        every { pluginManager.loadPlugins() } returns Unit
        every { pluginManager.startPlugins() } returns Unit
        every { pluginManager.plugins } returns emptyList()

        pluginManagerConfig.loadPlugins()

        verify(exactly = 1) { pluginManager.loadPlugins() }
    }

    @Test
    fun `loadPlugins should call startPlugins on pluginManager`() {
        every { pluginManager.loadPlugins() } returns Unit
        every { pluginManager.startPlugins() } returns Unit
        every { pluginManager.plugins } returns emptyList()

        pluginManagerConfig.loadPlugins()

        verify(exactly = 1) { pluginManager.startPlugins() }
    }

    @Test
    fun `loadPlugins should call loadPlugins before startPlugins`() {
        val callOrder = mutableListOf<String>()
        every { pluginManager.loadPlugins() } answers { callOrder.add("load") }
        every { pluginManager.startPlugins() } answers { callOrder.add("start") }
        every { pluginManager.plugins } returns emptyList()

        pluginManagerConfig.loadPlugins()

        assert(callOrder == listOf("load", "start"))
    }

    @Test
    fun `loadPlugins should handle plugins list from manager`() {
        val pluginWrapper = mockk<org.pf4j.PluginWrapper>()
        every { pluginWrapper.pluginId } returns "test-plugin"
        every { pluginManager.loadPlugins() } returns Unit
        every { pluginManager.startPlugins() } returns Unit
        every { pluginManager.plugins } returns listOf(pluginWrapper)

        pluginManagerConfig.loadPlugins()

        verify(exactly = 1) { pluginManager.plugins }
    }

    @Test
    fun `should be annotated with Configuration`() {
        val configAnnotation =
            PluginManagerConfig::class.java.getAnnotation(org.springframework.context.annotation.Configuration::class.java)
        assertNotNull(configAnnotation)
    }

    @Test
    fun `loadPlugins should be annotated with Async`() {
        val method = PluginManagerConfig::class.java.getDeclaredMethod("loadPlugins")
        val asyncAnnotation = method.getAnnotation(org.springframework.scheduling.annotation.Async::class.java)
        assertNotNull(asyncAnnotation)
    }

    @Test
    fun `loadPlugins should be annotated with EventListener for ApplicationReadyEvent`() {
        val method = PluginManagerConfig::class.java.getDeclaredMethod("loadPlugins")
        val eventListenerAnnotation = method.getAnnotation(org.springframework.context.event.EventListener::class.java)
        assertNotNull(eventListenerAnnotation)
    }

    @Test
    fun `loadPlugins should handle empty plugin list`() {
        every { pluginManager.loadPlugins() } returns Unit
        every { pluginManager.startPlugins() } returns Unit
        every { pluginManager.plugins } returns emptyList()

        pluginManagerConfig.loadPlugins()

        verify(exactly = 1) { pluginManager.loadPlugins() }
        verify(exactly = 1) { pluginManager.startPlugins() }
    }

    @Test
    fun `loadPlugins should handle multiple plugins`() {
        val plugin1 = mockk<org.pf4j.PluginWrapper>()
        val plugin2 = mockk<org.pf4j.PluginWrapper>()
        val plugin3 = mockk<org.pf4j.PluginWrapper>()

        every { plugin1.pluginId } returns "plugin1"
        every { plugin2.pluginId } returns "plugin2"
        every { plugin3.pluginId } returns "plugin3"
        every { pluginManager.loadPlugins() } returns Unit
        every { pluginManager.startPlugins() } returns Unit
        every { pluginManager.plugins } returns listOf(plugin1, plugin2, plugin3)

        pluginManagerConfig.loadPlugins()

        verify(exactly = 1) { pluginManager.loadPlugins() }
        verify(exactly = 1) { pluginManager.startPlugins() }
    }
}

