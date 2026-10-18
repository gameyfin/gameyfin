package org.gameyfin.app.core.tasks

import io.mockk.*
import org.gameyfin.app.config.entities.ConfigEntry
import org.gameyfin.app.config.persistence.ConfigRepository
import org.gameyfin.app.core.tasks.persistence.TaskScheduleEntry
import org.gameyfin.app.core.tasks.persistence.TaskScheduleRepository
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.support.CronTrigger
import java.util.*
import java.util.concurrent.ScheduledFuture
import kotlin.test.*

class TaskSchedulerServiceTest {

    private lateinit var taskScheduler: TaskScheduler
    private lateinit var registry: TaskRegistry
    private lateinit var scheduleRepository: TaskScheduleRepository
    private lateinit var runService: TaskRunService
    private lateinit var configRepository: ConfigRepository
    private lateinit var service: TaskSchedulerService

    private val schedules = mutableMapOf<String, TaskScheduleEntry>()
    private val scheduled = mutableListOf<Pair<Runnable, Trigger>>()
    private val futures = mutableListOf<ScheduledFuture<*>>()

    @BeforeEach
    fun setup() {
        schedules.clear()
        scheduled.clear()
        futures.clear()

        taskScheduler = mockk()
        every { taskScheduler.schedule(any(), any<Trigger>()) } answers {
            scheduled += firstArg<Runnable>() to secondArg<Trigger>()
            mockk<ScheduledFuture<*>>(relaxed = true).also { futures += it }
        }
        registry = TaskRegistry(mockk<ApplicationEventPublisher>(relaxed = true))
        scheduleRepository = mockk()
        every { scheduleRepository.findById(any()) } answers { Optional.ofNullable(schedules[firstArg()]) }
        every { scheduleRepository.existsById(any()) } answers { schedules.containsKey(firstArg()) }
        every { scheduleRepository.save(any()) } answers {
            firstArg<TaskScheduleEntry>().also { schedules[it.taskKey] = it }
        }
        runService = mockk(relaxed = true)
        configRepository = mockk(relaxed = true)
        every { configRepository.findById(any()) } returns Optional.empty()

        service = TaskSchedulerService(taskScheduler, registry, scheduleRepository, runService, configRepository)
    }

    private fun cronOf(trigger: Trigger) = (trigger as CronTrigger).expression

    @Test
    fun `getSchedule should return the default schedule if nothing has been configured`() {
        val definition = taskDefinition("test", defaultSchedule = "0 0 4 * * *")

        assertEquals(TaskSchedulerService.Schedule("0 0 4 * * *", true), service.getSchedule(definition))
        assertEquals(
            TaskSchedulerService.Schedule("0 0 4 * * *", false),
            service.getSchedule(taskDefinition("other", defaultSchedule = "0 0 4 * * *", scheduledByDefault = false))
        )
    }

    @Test
    fun `getSchedule should return the configured schedule`() {
        schedules["test"] = TaskScheduleEntry("test", "0 0 1 * * *", false)

        assertEquals(
            TaskSchedulerService.Schedule("0 0 1 * * *", false),
            service.getSchedule(taskDefinition("test", defaultSchedule = "0 0 4 * * *"))
        )
    }

    @Test
    fun `getSchedule should fall back to the default cron if only the state has been configured`() {
        schedules["test"] = TaskScheduleEntry("test", null, true)

        assertEquals(
            TaskSchedulerService.Schedule("0 0 4 * * *", true),
            service.getSchedule(taskDefinition("test", defaultSchedule = "0 0 4 * * *"))
        )
    }

    @Test
    fun `tasks should be scheduled once the application is ready`() {
        registry.register(taskDefinition("scheduled", defaultSchedule = "0 0 4 * * *"))
        registry.register(taskDefinition("manual"))
        registry.register(taskDefinition("disabled", defaultSchedule = "0 0 4 * * *", scheduledByDefault = false))

        service.onApplicationReady()

        assertEquals(listOf("0 0 4 * * *"), scheduled.map { cronOf(it.second) })
        assertNotNull(service.nextExecution(registry.require("scheduled")))
        assertNull(service.nextExecution(registry.require("manual")))
    }

    @Test
    fun `tasks registered after startup should be scheduled immediately`() {
        val definition = taskDefinition("late", defaultSchedule = "0 0 4 * * *")

        service.onTaskRegistered(TaskRegisteredEvent(definition))
        assertTrue(scheduled.isEmpty())

        service.onApplicationReady()
        service.onTaskRegistered(TaskRegisteredEvent(definition))
        assertEquals(1, scheduled.size)
    }

    @Test
    fun `scheduled executions should trigger the task`() {
        registry.register(taskDefinition("test", defaultSchedule = "0 0 4 * * *"))
        service.onApplicationReady()

        scheduled.single().first.run()

        verify { runService.trigger("test", TaskTrigger.SCHEDULE) }
    }

    @Test
    fun `failing scheduled executions should not propagate exceptions`() {
        registry.register(taskDefinition("test", defaultSchedule = "0 0 4 * * *"))
        every { runService.trigger(any(), any()) } throws IllegalStateException("Boom")
        service.onApplicationReady()

        scheduled.single().first.run()
    }

    @Test
    fun `updateSchedule should store and apply the new schedule`() {
        val definition = taskDefinition("test", defaultSchedule = "0 0 4 * * *")
        registry.register(definition)
        service.onApplicationReady()

        service.updateSchedule(definition, " 0 15 2 * * * ", true)

        assertEquals("0 15 2 * * *", schedules.getValue("test").cron)
        verify { futures.first().cancel(false) }
        assertEquals(listOf("0 0 4 * * *", "0 15 2 * * *"), scheduled.map { cronOf(it.second) })
    }

    @Test
    fun `updateSchedule should unschedule disabled tasks`() {
        val definition = taskDefinition("test", defaultSchedule = "0 0 4 * * *")
        registry.register(definition)
        service.onApplicationReady()

        service.updateSchedule(definition, null, false)

        assertFalse(schedules.getValue("test").enabled)
        assertNull(schedules.getValue("test").cron)
        verify { futures.single().cancel(false) }
        assertNull(service.nextExecution(definition))
    }

    @Test
    fun `updateSchedule should reject invalid schedules`() {
        val definition = taskDefinition("test")

        assertThrows<IllegalArgumentException> { service.updateSchedule(definition, "not a cron", true) }
        assertThrows<IllegalArgumentException> { service.updateSchedule(definition, null, true) }
        assertThrows<IllegalArgumentException> {
            service.updateSchedule(taskDefinition("other", schedulable = false), "0 0 4 * * *", true)
        }
        assertTrue(schedules.isEmpty())
    }

    @Test
    fun `unregistered tasks should be unscheduled and cancelled`() {
        val definition = taskDefinition("test", defaultSchedule = "0 0 4 * * *")
        registry.register(definition)
        service.onApplicationReady()

        service.onTaskUnregistered(TaskUnregisteredEvent(definition))

        verify { futures.single().cancel(false) }
        verify { runService.cancelAll("test") }
    }

    @Test
    fun `legacy library scan schedule should be migrated`() {
        val enabled = ConfigEntry(TaskSchedulerService.LEGACY_LIBRARY_SCAN_ENABLED_KEY, "false")
        val schedule = ConfigEntry(TaskSchedulerService.LEGACY_LIBRARY_SCAN_SCHEDULE_KEY, "0 0 2 * * *")
        every { configRepository.findById(TaskSchedulerService.LEGACY_LIBRARY_SCAN_ENABLED_KEY) } returns
                Optional.of(enabled)
        every { configRepository.findById(TaskSchedulerService.LEGACY_LIBRARY_SCAN_SCHEDULE_KEY) } returns
                Optional.of(schedule)

        service.onApplicationReady()

        val entry = schedules.getValue(LIBRARY_SCAN_TASK_KEY)
        assertEquals("0 0 2 * * *", entry.cron)
        assertFalse(entry.enabled)
        verify { configRepository.delete(enabled) }
        verify { configRepository.delete(schedule) }
    }

    @Test
    fun `legacy library scan schedule should not override an existing schedule`() {
        schedules[LIBRARY_SCAN_TASK_KEY] = TaskScheduleEntry(LIBRARY_SCAN_TASK_KEY, "0 0 5 * * *", true)
        val schedule = ConfigEntry(TaskSchedulerService.LEGACY_LIBRARY_SCAN_SCHEDULE_KEY, "0 0 2 * * *")
        every { configRepository.findById(TaskSchedulerService.LEGACY_LIBRARY_SCAN_SCHEDULE_KEY) } returns
                Optional.of(schedule)

        service.onApplicationReady()

        assertEquals("0 0 5 * * *", schedules.getValue(LIBRARY_SCAN_TASK_KEY).cron)
        verify { configRepository.delete(schedule) }
    }
}
