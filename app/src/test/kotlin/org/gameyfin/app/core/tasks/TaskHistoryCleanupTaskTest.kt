package org.gameyfin.app.core.tasks

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.tasks.logging.TaskRunLogs
import org.gameyfin.app.core.tasks.persistence.TaskRunEntry
import org.gameyfin.app.core.tasks.persistence.TaskRunRepository
import org.gameyfin.pluginapi.tasks.TaskContext
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.ApplicationEventPublisher
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskHistoryCleanupTaskTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var registry: TaskRegistry
    private lateinit var runRepository: TaskRunRepository
    private lateinit var runLogs: TaskRunLogs
    private lateinit var runService: TaskRunService
    private lateinit var task: TaskHistoryCleanupTask
    private lateinit var context: TaskContext

    private val currentRunId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        registry = TaskRegistry(mockk<ApplicationEventPublisher>(relaxed = true))
        runRepository = mockk(relaxed = true)
        val config = mockk<ConfigService>()
        every { config.get(ConfigProperties.Logs.Folder) } returns tempDir.toString()
        runLogs = TaskRunLogs(config)
        runService = mockk(relaxed = true)
        task = TaskHistoryCleanupTask(registry, runRepository, runLogs, runService)
        context = mockk(relaxed = true)
        every { context.runId } returns currentRunId
        every { context.setting<Int>(TaskHistoryCleanupTask.RETENTION_DAYS) } returns 30
        every { context.setting<Int>(TaskHistoryCleanupTask.MAX_RUNS_PER_TASK) } returns 2
        every { context.isCancellationRequested } returns false
    }

    private fun run(id: String, status: TaskRunStatus) = TaskRunEntry(
        id = id,
        taskKey = "test",
        displayName = "Test",
        link = null,
        trigger = TaskTrigger.SCHEDULE,
        status = status,
        startedAt = Instant.now().minus(Duration.ofDays(60))
    )

    private fun logFile(id: String) = tempDir.resolve(TaskRunLogs.TASK_LOG_DIRECTORY).resolve("$id.log")

    private fun createLog(id: String) {
        logFile(id).parent.createDirectories()
        logFile(id).writeText("log of $id")
    }

    @Test
    fun `register should add the task with its settings`() {
        task.register()

        val definition = registry.require(TaskHistoryCleanupTask.KEY)
        assertEquals(
            listOf(TaskHistoryCleanupTask.RETENTION_DAYS, TaskHistoryCleanupTask.MAX_RUNS_PER_TASK),
            definition.settings.map { it.key }
        )
        assertTrue(definition.scheduledByDefault)
    }

    @Test
    fun `execute should delete expired runs, surplus runs and orphaned logs`() {
        val expired = UUID.randomUUID().toString()
        val running = UUID.randomUUID().toString()
        val surplus = UUID.randomUUID().toString()
        val kept = listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val orphaned = UUID.randomUUID().toString()

        every { runRepository.findAllByStartedAtBefore(any()) } returns listOf(
            run(expired, TaskRunStatus.COMPLETED),
            run(running, TaskRunStatus.IN_PROGRESS)
        )
        every { runRepository.findAllTaskKeys() } returns listOf("test")
        every {
            runRepository.findIdsByTaskKeyAndStatusNotOrderByStartedAtDesc("test", TaskRunStatus.IN_PROGRESS)
        } returns kept + surplus
        every { runRepository.findAllIds() } returns kept + running + currentRunId.toString()
        (kept + expired + surplus + orphaned + currentRunId.toString()).forEach { createLog(it) }
        val deleted = slot<Collection<String>>()
        every { runRepository.deleteAllByIdIn(capture(deleted)) } returns 2

        val result = task.execute(context)

        assertEquals(setOf(expired, surplus), deleted.captured.toSet())
        verify { runService.forget(match { it.toSet() == setOf(expired, surplus) }) }
        assertEquals("2 run(s) deleted", result.summary)
        assertFalse(logFile(expired).exists())
        assertFalse(logFile(surplus).exists())
        assertFalse(logFile(orphaned).exists())
        assertTrue(logFile(currentRunId.toString()).exists())
        kept.forEach { assertTrue(logFile(it).exists()) }
    }

    @Test
    fun `execute should stop when cancelled`() {
        every { runRepository.findAllByStartedAtBefore(any()) } returns emptyList()
        every { runRepository.findAllTaskKeys() } returns listOf("test")
        every { context.throwIfCancelled() } throws org.gameyfin.pluginapi.tasks.TaskCancelledException()

        assertThrows<org.gameyfin.pluginapi.tasks.TaskCancelledException> { task.execute(context) }
        verify(exactly = 0) { runRepository.deleteAllByIdIn(any()) }
    }

    @Test
    fun `run logs should only be accessible by run ID`() {
        val id = UUID.randomUUID().toString()
        createLog(id)

        assertEquals("log of $id", runLogs.read(id))
        assertNull(runLogs.read(UUID.randomUUID().toString()))
        assertThrows<IllegalArgumentException> { runLogs.read("../../etc/passwd") }
        assertEquals(listOf(id), runLogs.listRunIds())
    }
}
