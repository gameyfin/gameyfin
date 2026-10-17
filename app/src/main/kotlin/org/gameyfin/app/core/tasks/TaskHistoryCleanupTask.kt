package org.gameyfin.app.core.tasks

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.gameyfin.app.core.tasks.logging.TaskRunLogs
import org.gameyfin.app.core.tasks.persistence.TaskRunRepository
import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.gameyfin.pluginapi.tasks.TaskContext
import org.gameyfin.pluginapi.tasks.TaskResult
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Deletes old task runs (and their logs) from the history.
 */
@Component
class TaskHistoryCleanupTask(
    private val registry: TaskRegistry,
    private val runRepository: TaskRunRepository,
    private val runLogs: TaskRunLogs,
    private val runService: TaskRunService
) {

    companion object {
        private val log = KotlinLogging.logger {}

        const val KEY = "task-history-cleanup"
        const val RETENTION_DAYS = "retentionDays"
        const val MAX_RUNS_PER_TASK = "maxRunsPerTask"
        private const val DELETE_BATCH_SIZE = 500
    }

    @PostConstruct
    fun register() {
        registry.register(
            TaskDefinition(
                key = KEY,
                name = "Task history cleanup",
                description = "Deletes old task runs and their logs from the history",
                defaultSchedule = "0 30 3 * * *",
                settings = listOf(
                    ConfigMetadata(
                        key = RETENTION_DAYS,
                        type = Int::class.javaObjectType,
                        label = "Retention (days)",
                        description = "Task runs older than this number of days will be deleted",
                        default = 30
                    ),
                    ConfigMetadata(
                        key = MAX_RUNS_PER_TASK,
                        type = Int::class.javaObjectType,
                        label = "Max. runs per task",
                        description = "Only this number of most recent runs will be kept per task",
                        default = 100
                    )
                ),
                action = TaskAction.Run { execute(it) }
            )
        )
    }

    fun execute(context: TaskContext): TaskResult {
        val retentionDays = (context.setting<Int>(RETENTION_DAYS) ?: 30).coerceAtLeast(0)
        val maxRunsPerTask = (context.setting<Int>(MAX_RUNS_PER_TASK) ?: 100).coerceAtLeast(1)
        val currentRunId = context.runId.toString()

        context.progress("Collecting expired task runs")
        val cutoff = Instant.now().minus(Duration.ofDays(retentionDays.toLong()))
        val expired = runRepository.findAllByStartedAtBefore(cutoff)
            .filter { it.status != TaskRunStatus.IN_PROGRESS }
            .map { it.id }
            .toMutableSet()

        for (taskKey in runRepository.findAllTaskKeys()) {
            context.throwIfCancelled()
            expired += runRepository
                .findIdsByTaskKeyAndStatusNotOrderByStartedAtDesc(taskKey, TaskRunStatus.IN_PROGRESS)
                .drop(maxRunsPerTask)
        }
        expired -= currentRunId
        log.info { "Deleting ${expired.size} task run(s) from the history" }

        val batches = expired.chunked(DELETE_BATCH_SIZE)
        batches.forEachIndexed { index, batch ->
            context.throwIfCancelled()
            context.progress("Deleting task runs", index, batches.size)
            runRepository.deleteAllByIdIn(batch)
            batch.forEach { runLogs.delete(it) }
            runService.forget(batch)
        }

        context.progress("Deleting orphaned log files")
        val knownRunIds = runRepository.findAllIds().toSet()
        val orphanedLogs = runLogs.listRunIds().filter { it !in knownRunIds && it != currentRunId }
        orphanedLogs.forEach { runLogs.delete(it) }
        if (orphanedLogs.isNotEmpty()) log.info { "Deleted ${orphanedLogs.size} orphaned log file(s)" }

        return TaskResult("${expired.size} run(s) deleted")
    }
}
