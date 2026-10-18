package org.gameyfin.app.core.plugins.management

import io.mockk.every
import io.mockk.mockk
import org.gameyfin.pluginapi.core.wrapper.GameyfinPlugin
import org.gameyfin.pluginapi.core.wrapper.PluginContext
import org.gameyfin.pluginapi.data.GameyfinDataService
import org.junit.jupiter.api.Test
import org.pf4j.Plugin
import org.pf4j.PluginDescriptor
import org.pf4j.PluginState
import org.pf4j.PluginWrapper
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class GameyfinPluginFactoryTest {

    class ContextPlugin(context: PluginContext) : GameyfinPlugin(context)

    class NoArgPlugin : Plugin()

    class FailingPlugin(context: PluginContext) : GameyfinPlugin(context) {
        init {
            throw IllegalStateException("boom")
        }
    }

    private fun mockWrapper(pluginClass: Class<*>): PluginWrapper {
        val descriptor = mockk<PluginDescriptor> {
            every { this@mockk.pluginClass } returns pluginClass.name
        }
        return mockk(relaxed = true) {
            every { pluginId } returns "test-plugin"
            every { this@mockk.descriptor } returns descriptor
            every { pluginClassLoader } returns javaClass.classLoader
            every { pluginState } returns PluginState.RESOLVED
        }
    }

    @Test
    fun `should instantiate plugin with PluginContext constructor`() {
        val dataService = mockk<GameyfinDataService>()
        val context = GameyfinPluginContext("test-plugin", "1.0.0", Path.of("plugindata", "test-plugin")) { dataService }
        val factory = GameyfinPluginFactory { context }

        val plugin = factory.create(mockWrapper(ContextPlugin::class.java))

        assertIs<ContextPlugin>(plugin)
        assertSame(context, plugin.context)
        assertSame(dataService, plugin.dataService)
        assertEquals(Path.of("plugindata", "test-plugin"), plugin.dataDirectory)
        assertEquals(Path.of("plugindata", "test-plugin", "state.json"), plugin.stateFile)
    }

    @Test
    fun `should fall back to no-arg constructor`() {
        val factory = GameyfinPluginFactory { error("Context must not be created") }

        val plugin = factory.create(mockWrapper(NoArgPlugin::class.java))

        assertIs<NoArgPlugin>(plugin)
    }

    @Test
    fun `should return null if plugin constructor throws`() {
        val context = GameyfinPluginContext("test-plugin", "1.0.0", Path.of("plugindata", "test-plugin")) { mockk() }
        val factory = GameyfinPluginFactory { context }

        assertNull(factory.create(mockWrapper(FailingPlugin::class.java)))
    }

    @Test
    fun `context should resolve data service lazily and only once`() {
        var calls = 0
        val dataService = mockk<GameyfinDataService>()
        val context = GameyfinPluginContext("test-plugin", "1.0.0", Path.of("plugindata", "test-plugin")) {
            calls++
            dataService
        }

        assertEquals(0, calls)
        assertSame(dataService, context.dataService)
        assertSame(dataService, context.dataService)
        assertEquals(1, calls)
    }
}
