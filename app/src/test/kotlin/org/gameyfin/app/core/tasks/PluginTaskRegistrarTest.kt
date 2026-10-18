package org.gameyfin.app.core.tasks

import io.mockk.*
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.gameyfin.pluginapi.tasks.GameSelection
import org.gameyfin.pluginapi.tasks.GameyfinTask
import org.gameyfin.pluginapi.tasks.TaskContext
import org.gameyfin.pluginapi.tasks.TaskResult
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.pf4j.PluginState
import org.pf4j.PluginStateEvent
import org.pf4j.PluginStateListener
import org.pf4j.PluginWrapper
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.*

class PluginTaskRegistrarTest {

    private class TestTask(override val key: String = "sync") : GameyfinTask {
        override val name = "Sync"
        override val description = "Synchronizes something"
        override val defaultSchedule = "0 0 3 * * *"
        override val cancellable = false
        override val parameters = listOf(ConfigMetadata("games", GameSelection::class.java, "Games", "", isRequired = false))
        var executions = 0

        override fun execute(context: TaskContext): TaskResult {
            executions++
            return TaskResult("Synced")
        }
    }

    private lateinit var pluginManager: GameyfinPluginManager
    private lateinit var registry: TaskRegistry
    private lateinit var runService: TaskRunService
    private lateinit var registrar: PluginTaskRegistrar
    private val listener = slot<PluginStateListener>()

    @BeforeEach
    fun setup() {
        pluginManager = mockk(relaxed = true)
        every { pluginManager.addPluginStateListener(capture(listener)) } just Runs
        every { pluginManager.startedPlugins } returns emptyList()
        every { pluginManager.getPlugin(any()) } returns null
        every { pluginManager.getExtensions(GameyfinTask::class.java, any<String>()) } returns emptyList()
        registry = TaskRegistry(mockk<ApplicationEventPublisher>(relaxed = true))
        runService = mockk(relaxed = true)
        registrar = PluginTaskRegistrar(pluginManager, registry, runService)
    }

    private fun stateEvent(pluginId: String, state: PluginState): PluginStateEvent {
        val plugin = mockk<PluginWrapper> { every { this@mockk.pluginId } returns pluginId }
        return PluginStateEvent(pluginManager, plugin, PluginState.RESOLVED).also {
            every { plugin.pluginState } returns state
        }
    }

    @Test
    fun `tasks of already started plugins should be registered`() {
        val task = TestTask()
        val plugin = mockk<PluginWrapper> { every { pluginId } returns "my-plugin" }
        every { pluginManager.startedPlugins } returns listOf(plugin)
        every { pluginManager.getExtensions(GameyfinTask::class.java, "my-plugin") } returns listOf(task)

        registrar.init()

        val definition = registry.require(PluginTaskRegistrar.taskKey("my-plugin", "sync"))
        assertEquals("plugin.my-plugin.sync", definition.key)
        assertEquals("Sync", definition.name)
        assertEquals("Synchronizes something", definition.description)
        assertEquals(TaskSource.PLUGIN, definition.source)
        assertEquals("my-plugin", definition.pluginId)
        assertEquals("0 0 3 * * *", definition.defaultSchedule)
        assertTrue(definition.scheduledByDefault)
        assertFalse(definition.cancellable)
        assertEquals(listOf("games"), definition.parameters.map { it.key })

        val result = (definition.action as TaskAction.Run).execute(mockk())
        assertEquals("Synced", result?.summary)
        assertEquals(1, task.executions)
    }

    @Test
    fun `tasks should be registered when a plugin is started and removed when it is stopped`() {
        every { pluginManager.getExtensions(GameyfinTask::class.java, "my-plugin") } returns listOf(TestTask())
        registrar.init()
        assertTrue(registry.getAll().isEmpty())

        listener.captured.pluginStateChanged(stateEvent("my-plugin", PluginState.STARTED))
        assertNotNull(registry.get("plugin.my-plugin.sync"))

        listener.captured.pluginStateChanged(stateEvent("my-plugin", PluginState.STOPPED))
        assertNull(registry.get("plugin.my-plugin.sync"))
        verify { runService.cancelAll("plugin.my-plugin.sync") }
    }

    @Test
    fun `tasks of different plugins should not collide`() {
        every { pluginManager.getExtensions(GameyfinTask::class.java, "a") } returns listOf(TestTask())
        every { pluginManager.getExtensions(GameyfinTask::class.java, "b") } returns listOf(TestTask())
        registrar.init()

        listener.captured.pluginStateChanged(stateEvent("a", PluginState.STARTED))
        listener.captured.pluginStateChanged(stateEvent("b", PluginState.STARTED))
        listener.captured.pluginStateChanged(stateEvent("a", PluginState.STOPPED))

        assertEquals(listOf("plugin.b.sync"), registry.getAll().map { it.key })
    }

    @Test
    fun `failures while loading the tasks of a plugin should be ignored`() {
        every { pluginManager.getExtensions(GameyfinTask::class.java, "broken") } throws NoClassDefFoundError("x")
        registrar.init()

        listener.captured.pluginStateChanged(stateEvent("broken", PluginState.STARTED))

        assertTrue(registry.getAll().isEmpty())
    }

    @Test
    fun `plugin tasks should be triggered via the task framework`() {
        every { pluginManager.getExtensions(GameyfinTask::class.java, "my-plugin") } returns listOf(TestTask())
        registrar.init()
        listener.captured.pluginStateChanged(stateEvent("my-plugin", PluginState.STARTED))

        val runRepository = inMemoryTaskRunRepository()
        val realRunService = TaskRunService(registry, runRepository, mockk())
        try {
            val run = realRunService.trigger("plugin.my-plugin.sync", TaskTrigger.MANUAL)
            assertNotNull(run)
            assertEquals("Sync", run.taskName)
        } finally {
            realRunService.shutdown()
        }
    }
}
