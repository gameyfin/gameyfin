package org.gameyfin.app.core.tasks

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gameyfin.app.core.tasks.dto.TaskStepDto
import org.gameyfin.app.core.tasks.persistence.TaskRunEntry
import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.gameyfin.pluginapi.tasks.GameSelection
import org.gameyfin.pluginapi.tasks.TaskCancelledException
import org.gameyfin.pluginapi.tasks.TaskResult
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class TaskRunServiceTest {

    private lateinit var registry: TaskRegistry
    private lateinit var settingsService: TaskSettingsService
    private lateinit var store: MutableMap<String, TaskRunEntry>
    private lateinit var service: TaskRunService

    @BeforeEach
    fun setup() {
        registry = TaskRegistry(mockk<ApplicationEventPublisher>(relaxed = true))
        settingsService = mockk()
        store = ConcurrentHashMap()
        service = TaskRunService(registry, inMemoryTaskRunRepository(store), settingsService)
    }

    @AfterEach
    fun teardown() {
        service.shutdown()
    }

    private fun awaitFinished(runId: String) {
        val deadline = System.currentTimeMillis() + 5000
        while (service.getActiveRun(runId) != null) {
            check(System.currentTimeMillis() < deadline) { "Run $runId did not finish in time" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `runBlocking should persist a completed run with its summary`() {
        registry.register(taskDefinition("test"))

        val run = service.runBlocking("test", TaskTrigger.MANUAL, displayName = "Test run", link = "/x") {
            TaskResult("All done")
        }

        assertEquals(TaskRunStatus.COMPLETED, run.status)
        assertEquals("All done", run.summary)
        assertEquals("Test run", run.displayName)
        assertNotNull(run.finishedAt)
        val entry = store.getValue(run.id)
        assertEquals(TaskRunStatus.COMPLETED, entry.status)
        assertEquals("All done", entry.summary)
        assertEquals("/x", entry.link)
        assertEquals(TaskTrigger.MANUAL, entry.trigger)
        assertTrue(service.getActiveRuns().isEmpty())
    }

    @Test
    fun `runBlocking should mark the run as failed and rethrow the exception`() {
        registry.register(taskDefinition("test"))

        val exception = assertThrows<IllegalStateException> {
            service.runBlocking("test", TaskTrigger.MANUAL) { throw IllegalStateException("Boom") }
        }

        assertEquals("Boom", exception.message)
        val entry = store.values.single()
        assertEquals(TaskRunStatus.FAILED, entry.status)
        assertEquals("Boom", entry.errorMessage)
    }

    @Test
    fun `runBlocking should not rethrow if disabled`() {
        registry.register(taskDefinition("test"))

        val run = service.runBlocking("test", TaskTrigger.MANUAL, rethrow = false) { throw RuntimeException("Boom") }

        assertEquals(TaskRunStatus.FAILED, run.status)
        assertEquals("Boom", run.errorMessage)
    }

    @Test
    fun `cancelled runs should be recorded as cancelled even if the task returns normally`() {
        registry.register(taskDefinition("test"))
        val started = CountDownLatch(1)

        val run = service.start("test", TaskTrigger.MANUAL) { context ->
            started.countDown()
            while (!context.isCancellationRequested) Thread.sleep(5)
            TaskResult("Partial")
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))

        service.cancel(run.id)
        awaitFinished(run.id)

        assertEquals(TaskRunStatus.CANCELLED, store.getValue(run.id).status)
    }

    @Test
    fun `forEachItem should not process items after the cancellation was requested`() {
        registry.register(taskDefinition("test"))
        val started = CountDownLatch(1)
        val processed = mutableListOf<Int>()

        val run = service.start("test", TaskTrigger.MANUAL) { context ->
            started.countDown()
            while (!context.isCancellationRequested) Thread.sleep(5)
            context.forEachItem(listOf(1, 2, 3)) { processed.add(it) }
            null
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))

        service.cancel(run.id)
        awaitFinished(run.id)

        assertEquals(emptyList(), processed)
        assertEquals(TaskRunStatus.CANCELLED, store.getValue(run.id).status)
    }

    @Test
    fun `wrap should not execute work once the cancellation was requested`() {
        registry.register(taskDefinition("test"))
        val started = CountDownLatch(1)
        val executed = java.util.concurrent.atomic.AtomicBoolean(false)

        val run = service.start("test", TaskTrigger.MANUAL) { context ->
            started.countDown()
            while (!context.isCancellationRequested) Thread.sleep(5)
            assertThrows<TaskCancelledException> {
                context.wrap(Runnable { executed.set(true) }).run()
            }
            assertThrows<TaskCancelledException> {
                context.wrap(java.util.concurrent.Callable { executed.set(true) }).call()
            }
            throw TaskCancelledException()
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))

        service.cancel(run.id)
        awaitFinished(run.id)

        assertFalse(executed.get())
        assertEquals(TaskRunStatus.CANCELLED, store.getValue(run.id).status)
    }

    @Test
    fun `wrap should execute work while the run is not cancelled`() {
        registry.register(taskDefinition("test"))
        var result: String? = null

        service.runBlocking("test", TaskTrigger.MANUAL) { context ->
            result = context.wrap(java.util.concurrent.Callable { "ok" }).call()
            null
        }

        assertEquals("ok", result)
    }

    @Test
    fun `runBlocking should mark the run as cancelled on TaskCancelledException`() {
        registry.register(taskDefinition("test"))

        val run = service.runBlocking("test", TaskTrigger.MANUAL) { throw TaskCancelledException("Stopped early") }

        assertEquals(TaskRunStatus.CANCELLED, run.status)
        assertEquals("Stopped early", run.summary)
        assertNull(run.errorMessage)
    }

    @Test
    fun `runBlocking should fail for unknown tasks`() {
        assertThrows<IllegalArgumentException> {
            service.runBlocking("unknown", TaskTrigger.MANUAL) { null }
        }
        assertTrue(store.isEmpty())
    }

    @Test
    fun `start should execute the run in the background`() {
        registry.register(taskDefinition("test"))
        val latch = CountDownLatch(1)

        val run = service.start("test", TaskTrigger.MANUAL) {
            latch.await(5, TimeUnit.SECONDS)
            TaskResult("Done")
        }

        assertEquals(TaskRunStatus.IN_PROGRESS, run.status)
        assertTrue(service.isRunning("test"))
        assertEquals(TaskRunStatus.IN_PROGRESS, store.getValue(run.id).status)

        latch.countDown()
        awaitFinished(run.id)

        assertFalse(service.isRunning("test"))
        assertEquals(TaskRunStatus.COMPLETED, store.getValue(run.id).status)
        assertEquals("Done", store.getValue(run.id).summary)
    }

    @Test
    fun `exclusive runs should not be started while the task is running`() {
        registry.register(taskDefinition("test"))
        val latch = CountDownLatch(1)
        val first = service.start("test", TaskTrigger.MANUAL, exclusive = true) { latch.await(5, TimeUnit.SECONDS); null }

        assertThrows<TaskAlreadyRunningException> {
            service.start("test", TaskTrigger.MANUAL, exclusive = true) { null }
        }
        // Non-exclusive runs (e.g. scans of different libraries) can run in parallel
        val second = service.start("test", TaskTrigger.MANUAL) { latch.await(5, TimeUnit.SECONDS); null }
        assertEquals(2, service.getActiveRuns().size)

        latch.countDown()
        awaitFinished(first.id)
        awaitFinished(second.id)
    }

    @Test
    fun `cancel should request the cancellation of a running run`() {
        registry.register(taskDefinition("test"))
        val started = CountDownLatch(1)

        val run = service.start("test", TaskTrigger.MANUAL) { context ->
            started.countDown()
            while (!context.isCancellationRequested) Thread.sleep(5)
            context.throwIfCancelled()
            null
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))

        service.cancel(run.id)
        assertEquals(true, service.getActiveRun(run.id)?.cancelRequested ?: true)
        awaitFinished(run.id)

        assertEquals(TaskRunStatus.CANCELLED, store.getValue(run.id).status)
    }

    @Test
    fun `cancel should fail for runs which are not cancellable`() {
        registry.register(taskDefinition("test", cancellable = false))
        val latch = CountDownLatch(1)
        val run = service.start("test", TaskTrigger.MANUAL) { latch.await(5, TimeUnit.SECONDS); null }

        assertFalse(run.cancellable)
        assertThrows<IllegalStateException> { service.cancel(run.id) }

        latch.countDown()
        awaitFinished(run.id)
        assertEquals(TaskRunStatus.COMPLETED, store.getValue(run.id).status)
    }

    @Test
    fun `cancel should fail for runs which are not running`() {
        assertThrows<IllegalArgumentException> { service.cancel("does-not-exist") }
    }

    @Test
    fun `cancelAll should request the cancellation of all runs of a task`() {
        registry.register(taskDefinition("test"))
        val body: (org.gameyfin.pluginapi.tasks.TaskContext) -> TaskResult? = { context ->
            while (!context.isCancellationRequested) Thread.sleep(5)
            context.throwIfCancelled()
            null
        }
        val runs = listOf(
            service.start("test", TaskTrigger.MANUAL, body = body),
            service.start("test", TaskTrigger.MANUAL, body = body)
        )

        service.cancelAll("test")

        runs.forEach { awaitFinished(it.id) }
        runs.forEach { assertEquals(TaskRunStatus.CANCELLED, store.getValue(it.id).status) }
    }

    @Test
    fun `trigger should start an exclusive run of the task`() {
        val latch = CountDownLatch(1)
        registry.register(taskDefinition("test", action = TaskAction.Run { latch.await(5, TimeUnit.SECONDS); null }))

        val run = service.trigger("test", TaskTrigger.MANUAL)
        assertNotNull(run)
        assertEquals(TaskTrigger.MANUAL, run.trigger)

        // Scheduled executions are skipped while the task is running, manual ones are rejected
        assertNull(service.trigger("test", TaskTrigger.SCHEDULE))
        assertThrows<TaskAlreadyRunningException> { service.trigger("test", TaskTrigger.MANUAL) }

        latch.countDown()
        awaitFinished(run.id)
        assertEquals(1, store.size)
    }

    @Test
    fun `trigger should delegate to tasks which dispatch their runs themselves`() {
        val triggers = mutableListOf<TaskTrigger>()
        registry.register(taskDefinition("test", action = TaskAction.Dispatch { triggers += it }))

        assertNull(service.trigger("test", TaskTrigger.SCHEDULE))

        assertEquals(listOf(TaskTrigger.SCHEDULE), triggers)
        assertTrue(store.isEmpty())
    }

    @Test
    fun `trigger should reject manual execution of tasks which can not be started manually`() {
        registry.register(taskDefinition("test", manuallyTriggerable = false))

        assertThrows<IllegalStateException> { service.trigger("test", TaskTrigger.MANUAL) }
        assertTrue(store.isEmpty())
    }

    @Test
    fun `context should provide progress and settings of the task`() {
        val definition = taskDefinition("test")
        registry.register(definition)
        every { settingsService.get<Int>(definition, "limit") } returns 42
        service.minEmitInterval = java.time.Duration.ZERO
        var step: TaskStepDto? = null
        var limit: Int? = null

        service.runBlocking("test", TaskTrigger.MANUAL) { context ->
            context.progress("Working", 1, 3)
            step = service.getActiveRun(context.runId.toString())?.step
            limit = context.setting("limit")
            null
        }

        assertEquals(TaskStepDto("Working", 1, 3), step)
        assertEquals(42, limit)
    }

    @Test
    fun `subscribe should emit updates of runs`() {
        registry.register(taskDefinition("test"))
        val updates = mutableListOf<org.gameyfin.app.core.tasks.dto.TaskRunDto>()
        val subscription = service.subscribe().subscribe { synchronized(updates) { updates += it } }

        try {
            val run = service.runBlocking("test", TaskTrigger.MANUAL) { null }

            val deadline = System.currentTimeMillis() + 5000
            while (synchronized(updates) { updates.none { it.id == run.id && it.status == TaskRunStatus.COMPLETED } }) {
                check(System.currentTimeMillis() < deadline) { "No update received" }
                Thread.sleep(50)
            }
        } finally {
            subscription.dispose()
        }
    }

    @Test
    fun `start should fail after shutdown`() {
        registry.register(taskDefinition("test"))
        service.shutdown()

        assertThrows<IllegalStateException> { service.start("test", TaskTrigger.MANUAL) { null } }
        assertEquals(TaskRunStatus.FAILED, store.values.single().status)
    }

    @Test
    fun `init should mark interrupted runs as failed`() {
        val entry = TaskRunEntry(
            id = "11111111-1111-1111-1111-111111111111",
            taskKey = "test",
            displayName = "Test",
            link = null,
            trigger = TaskTrigger.SCHEDULE,
            status = TaskRunStatus.IN_PROGRESS,
            startedAt = Instant.now()
        )
        store[entry.id] = entry

        service.init()

        assertEquals(TaskRunStatus.FAILED, entry.status)
        assertNotNull(entry.finishedAt)
        assertNotNull(entry.errorMessage)
    }

    @Test
    fun `toDto should fall back to the task key for unknown tasks`() {
        val repository = inMemoryTaskRunRepository()
        val dto = TaskRunService(registry, repository, settingsService).toDto(
            TaskRunEntry(
                id = "11111111-1111-1111-1111-111111111111",
                taskKey = "removed-plugin-task",
                displayName = "Old run",
                link = null,
                trigger = TaskTrigger.MANUAL,
                status = TaskRunStatus.COMPLETED,
                startedAt = Instant.now()
            )
        )

        assertEquals("removed-plugin-task", dto.taskName)
        assertFalse(dto.cancellable)
        verify(exactly = 0) { repository.save(any()) }
    }

    private fun parameterizedTask(onExecute: (GameSelection?, Int?) -> Unit) = TaskDefinition(
        key = "test",
        name = "Test",
        parameters = listOf(
            ConfigMetadata("games", GameSelection::class.java, "Games", "", isRequired = false),
            ConfigMetadata("limit", Int::class.javaObjectType, "Limit", "", default = 5)
        ),
        action = TaskAction.Run { context ->
            onExecute(context.parameter("games"), context.parameter("limit"))
            null
        }
    )

    @Test
    fun `trigger should pass the parameters to the run`() {
        var games: GameSelection? = null
        var limit: Int? = null
        registry.register(parameterizedTask { g, l -> games = g; limit = l })

        val run = service.trigger("test", TaskTrigger.MANUAL, mapOf("games" to "1,2", "limit" to "3"))
        assertNotNull(run)
        awaitFinished(run.id)

        assertEquals(TaskRunStatus.COMPLETED, store.getValue(run.id).status)
        assertEquals(GameSelection(listOf(1L, 2L)), games)
        assertEquals(3, limit)
    }

    @Test
    fun `trigger should use the default values of missing parameters`() {
        var games: GameSelection? = GameSelection(listOf(1L))
        var limit: Int? = null
        registry.register(parameterizedTask { g, l -> games = g; limit = l })

        val run = service.trigger("test", TaskTrigger.SCHEDULE)
        assertNotNull(run)
        awaitFinished(run.id)

        assertNull(games)
        assertEquals(5, limit)
    }

    @Test
    fun `trigger should reject invalid parameters`() {
        registry.register(parameterizedTask { _, _ -> })

        assertThrows<IllegalArgumentException> { service.trigger("test", TaskTrigger.MANUAL, mapOf("games" to "abc")) }
        assertThrows<IllegalArgumentException> { service.trigger("test", TaskTrigger.MANUAL, mapOf("unknown" to "1")) }
        assertTrue(store.isEmpty())
    }

    @Test
    fun `context should reject undeclared parameters`() {
        registry.register(taskDefinition("test"))

        assertThrows<IllegalArgumentException> {
            service.runBlocking("test", TaskTrigger.MANUAL) { context -> context.parameter<Int>("limit"); null }
        }
    }
}
