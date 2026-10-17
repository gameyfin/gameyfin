package org.gameyfin.app.core.tasks

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gameyfin.app.core.tasks.dto.TaskRunDto
import org.gameyfin.app.core.tasks.dto.TaskUpdateDto
import org.gameyfin.app.core.tasks.logging.TaskRunLogs
import org.gameyfin.app.core.tasks.persistence.TaskRunRepository
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskServiceTest {

    private lateinit var registry: TaskRegistry
    private lateinit var runService: TaskRunService
    private lateinit var schedulerService: TaskSchedulerService
    private lateinit var settingsService: TaskSettingsService
    private lateinit var runRepository: TaskRunRepository
    private lateinit var runLogs: TaskRunLogs
    private lateinit var pluginTaskRegistrar: PluginTaskRegistrar
    private lateinit var service: TaskService

    @BeforeEach
    fun setup() {
        registry = TaskRegistry(mockk<ApplicationEventPublisher>(relaxed = true))
        runService = mockk(relaxed = true)
        schedulerService = mockk(relaxed = true)
        every { schedulerService.getSchedule(any()) } returns TaskSchedulerService.Schedule(null, false)
        settingsService = mockk(relaxed = true)
        every { settingsService.validate(any(), any()) } returns emptyMap()
        every { settingsService.getRaw(any()) } returns emptyMap()
        runRepository = mockk(relaxed = true)
        every { runRepository.findFirstByTaskKeyOrderByStartedAtDesc(any()) } returns null
        runLogs = mockk(relaxed = true)
        pluginTaskRegistrar = mockk(relaxed = true)
        service = TaskService(
            registry, runService, schedulerService, settingsService, runRepository, runLogs, pluginTaskRegistrar
        )
    }

    private fun activeRun(id: String, cancellable: Boolean) = TaskRunDto(
        id = id,
        taskKey = "test",
        taskName = "Test",
        displayName = "Test",
        link = null,
        trigger = TaskTrigger.MANUAL,
        status = TaskRunStatus.IN_PROGRESS,
        step = null,
        startedAt = Instant.now(),
        finishedAt = null,
        summary = null,
        errorMessage = null,
        cancellable = cancellable,
        cancelRequested = false
    )

    @Test
    fun `getTasks should list core tasks before plugin tasks`() {
        registry.register(taskDefinition("b"))
        registry.register(
            TaskDefinition(
                key = "plugin.x.a",
                name = "A plugin task",
                source = TaskSource.PLUGIN,
                pluginId = "x",
                action = TaskAction.Run { null })
        )
        registry.register(taskDefinition("a"))
        every { pluginTaskRegistrar.pluginName("x") } returns "Plugin X"

        val tasks = service.getTasks()

        assertEquals(listOf("a", "b", "plugin.x.a"), tasks.map { it.key })
        assertEquals("Plugin X", tasks.last().pluginName)
    }

    @Test
    fun `updateTask should validate the settings before changing anything`() {
        val definition = taskDefinition("test")
        registry.register(definition)
        every { settingsService.validate(definition, any()) } returns mapOf("limit" to "Invalid")

        assertThrows<IllegalArgumentException> {
            service.updateTask(TaskUpdateDto("test", "0 0 1 * * *", true, mapOf("limit" to "x")))
        }

        verify(exactly = 0) { schedulerService.updateSchedule(any(), any(), any()) }
        verify(exactly = 0) { settingsService.update(any(), any()) }
    }

    @Test
    fun `updateTask should update schedule and settings`() {
        val definition = taskDefinition("test")
        registry.register(definition)

        service.updateTask(TaskUpdateDto("test", "0 0 1 * * *", true, mapOf("limit" to "5")))

        verify { schedulerService.updateSchedule(definition, "0 0 1 * * *", true) }
        verify { settingsService.update(definition, mapOf("limit" to "5")) }
    }

    @Test
    fun `updateTask should not change the schedule of tasks which can not be scheduled`() {
        registry.register(taskDefinition("test", schedulable = false))

        service.updateTask(TaskUpdateDto("test", null, false, null))

        verify(exactly = 0) { schedulerService.updateSchedule(any(), any(), any()) }
        verify(exactly = 0) { settingsService.update(any(), any()) }
    }

    @Test
    fun `runNow should trigger the task manually`() {
        registry.register(taskDefinition("test"))

        service.runNow("test")

        verify { runService.trigger("test", TaskTrigger.MANUAL) }
    }

    @Test
    fun `runNow should reject tasks which can not be started manually`() {
        registry.register(taskDefinition("test", manuallyTriggerable = false))

        assertThrows<IllegalArgumentException> { service.runNow("test") }
        assertThrows<IllegalArgumentException> { service.runNow("unknown") }
        verify(exactly = 0) { runService.trigger(any(), any()) }
    }

    @Test
    fun `cancelRun should only cancel cancellable active runs`() {
        every { runService.getActiveRun("a") } returns activeRun("a", cancellable = true)
        every { runService.getActiveRun("b") } returns activeRun("b", cancellable = false)
        every { runService.getActiveRun("c") } returns null

        service.cancelRun("a")
        assertThrows<IllegalArgumentException> { service.cancelRun("b") }
        assertThrows<IllegalArgumentException> { service.cancelRun("c") }

        verify(exactly = 1) { runService.cancel(any()) }
        verify { runService.cancel("a") }
    }

    @Test
    fun `getRuns should clamp the page size and filter by task`() {
        every { runRepository.findAllByTaskKeyOrderByStartedAtDesc("test", PageRequest.of(0, 100)) } returns
                SliceImpl(emptyList(), PageRequest.of(0, 100), true)
        every { runRepository.findAllByOrderByStartedAtDesc(PageRequest.of(2, 1)) } returns
                SliceImpl(emptyList(), PageRequest.of(2, 1), false)

        assertTrue(service.getRuns("test", -1, 1000).hasMore)
        assertFalse(service.getRuns(null, 2, 0).hasMore)
    }
}
