package org.gameyfin.app.core.tasks

import ch.qos.logback.classic.ClassicConstants
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.gameyfin.app.core.tasks.dto.TaskRunDto
import org.gameyfin.app.core.tasks.dto.TaskStepDto
import org.gameyfin.app.core.tasks.logging.MdcPropagation
import org.gameyfin.app.core.tasks.persistence.TaskRunEntry
import org.gameyfin.app.core.tasks.persistence.TaskRunRepository
import org.gameyfin.pluginapi.tasks.TaskCancelledException
import org.gameyfin.pluginapi.tasks.TaskContext
import org.gameyfin.pluginapi.tasks.TaskResult
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.io.Serializable
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.*

/**
 * Executes task runs, keeps track of their progress and publishes it to subscribers.
 *
 * Everything logged on the thread executing a run (and on threads using [TaskContext.wrap]) is written into the
 * log file of the run, see [org.gameyfin.app.core.tasks.logging.TaskRunLogs].
 */
@Service
class TaskRunService(
    private val registry: TaskRegistry,
    private val runRepository: TaskRunRepository,
    private val settingsService: TaskSettingsService
) {

    companion object {
        private val log = KotlinLogging.logger {}
        private val finalizeLogger = LoggerFactory.getLogger(TaskRunService::class.java)

        private const val MAX_TEXT_LENGTH = 4000
        private val RECENT_RUNS_RETENTION: Duration = Duration.ofHours(24)
        private const val RECENT_RUNS_LIMIT = 50
    }

    /** Progress updates are rate-limited, except when the step changes */
    internal var minEmitInterval: Duration = Duration.ofMillis(250)

    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
    private val activeRuns = ConcurrentHashMap<String, ActiveRun>()
    private val recentRuns = ConcurrentHashMap<String, TaskRunDto>()
    private val startLock = Any()

    // Events are not buffered while nobody is subscribed, new subscribers receive the current snapshot instead
    private val events = Sinks.many().multicast().directBestEffort<TaskRunDto>()

    /**
     * Runs which were still in progress when Gameyfin was stopped can never finish, so they are marked as failed.
     */
    @PostConstruct
    fun init() {
        try {
            runRepository.findAllByStatus(TaskRunStatus.IN_PROGRESS).forEach {
                it.status = TaskRunStatus.FAILED
                it.finishedAt = Instant.now()
                it.errorMessage = "Interrupted because Gameyfin has been stopped"
                runRepository.save(it)
            }
            runRepository.findAllByFinishedAtAfterOrderByStartedAtDesc(
                Instant.now().minus(RECENT_RUNS_RETENTION),
                PageRequest.of(0, RECENT_RUNS_LIMIT)
            ).forEach { recentRuns[it.id] = toDto(it) }
        } catch (e: Exception) {
            log.warn { "Could not load the history of task runs: ${e.message}" }
        }
    }

    @PreDestroy
    fun shutdown() {
        activeRuns.values.forEach { it.cancelRequested = true }
        executor.shutdownNow()
    }

    fun subscribe(): Flux<List<TaskRunDto>> {
        // Subscribe to live updates before taking the snapshot, so no update can get lost in between
        val snapshot = Flux.defer {
            // History is loaded before all task definitions have necessarily been registered.
            val recent = recentRuns.values.map {
                it.copy(visibleInOverview = registry.get(it.taskKey)?.visibleInOverview ?: true)
            }
            Flux.fromIterable(recent + activeRuns.values.map { it.toDto() })
        }
        return Flux.merge(events.asFlux(), snapshot)
            .buffer(Duration.ofSeconds(1))
            .filter { it.isNotEmpty() }
    }

    fun getActiveRuns(): List<TaskRunDto> = activeRuns.values.map { it.toDto() }

    fun getActiveRun(runId: String): TaskRunDto? = activeRuns[runId]?.toDto()

    fun isRunning(taskKey: String): Boolean = activeRuns.values.any { it.definition.key == taskKey }

    /**
     * Executes a task as configured, used by the schedule and when it is started manually.
     *
     * @return the started run, or `null` if the task dispatches its runs itself or if a scheduled execution has
     * been skipped because the task is still running
     * @param parameters raw values of the parameters declared by the task, missing ones use their default values
     * @throws IllegalStateException if the task is started manually while it is still running
     * @throws IllegalArgumentException if the parameters are invalid
     */
    fun trigger(taskKey: String, trigger: TaskTrigger, parameters: Map<String, String?> = emptyMap()): TaskRunDto? {
        val definition = registry.require(taskKey)
        check(!(trigger == TaskTrigger.MANUAL && !definition.manuallyTriggerable)) { "Task ${definition.name} can not be started manually" }

        // Scheduled runs use the default values of the parameters
        val parameterValues = TaskSettingsService.convertValues(definition.parameters, parameters)
        return when (val action = definition.action) {
            is TaskAction.Dispatch -> {
                action.dispatch(trigger)
                null
            }

            is TaskAction.Run -> try {
                val run = begin(taskKey, trigger, null, null, null, exclusive = true, parameterValues)
                submit(run) { action.execute(it) }
            } catch (e: TaskAlreadyRunningException) {
                if (trigger != TaskTrigger.MANUAL) {
                    log.info { "Skipping execution of task ${definition.name}, it is still running" }
                    null
                } else throw e
            }
        }
    }

    /**
     * Starts a run of a task in the background.
     *
     * @param exclusive whether the run must not be started while another run of the same task is in progress
     * @param body the work to execute, its result is shown as summary of the run
     */
    fun start(
        taskKey: String,
        trigger: TaskTrigger,
        displayName: String? = null,
        link: String? = null,
        cancellable: Boolean? = null,
        exclusive: Boolean = false,
        body: (TaskContext) -> TaskResult?
    ): TaskRunDto {
        val run = begin(taskKey, trigger, displayName, link, cancellable, exclusive)
        return submit(run, body)
    }

    private fun submit(run: ActiveRun, body: (TaskContext) -> TaskResult?): TaskRunDto {
        try {
            executor.execute { execute(run, body, rethrow = false) }
        } catch (e: RejectedExecutionException) {
            finish(run, TaskRunStatus.FAILED, null, "Gameyfin is shutting down")
            throw IllegalStateException("Gameyfin is shutting down", e)
        }
        return run.toDto()
    }

    /**
     * Executes a run of a task on the calling thread.
     *
     * @param rethrow whether exceptions thrown by [body] are rethrown after the run has been marked as failed
     */
    fun runBlocking(
        taskKey: String,
        trigger: TaskTrigger,
        displayName: String? = null,
        link: String? = null,
        cancellable: Boolean? = null,
        rethrow: Boolean = true,
        body: (TaskContext) -> TaskResult?
    ): TaskRunDto {
        val run = begin(taskKey, trigger, displayName, link, cancellable, exclusive = false)
        execute(run, body, rethrow)
        return recentRuns[run.id] ?: run.toDto()
    }

    /**
     * Requests the cancellation of a run. Cancellation is cooperative, the run stops as soon as it checks for it.
     */
    fun cancel(runId: String) {
        val run = activeRuns[runId] ?: throw IllegalArgumentException("Task run $runId is not running")
        check(run.cancellable) { "${run.displayName} can not be cancelled" }
        if (run.cancelRequested) return
        run.cancelRequested = true
        MdcPropagation.withTaskRun(run.id) { log.info { "Cancellation of ${run.displayName} has been requested" } }
        emit(run, force = true)
    }

    /** Requests the cancellation of all runs of a task, e.g. because the plugin providing it has been stopped */
    fun cancelAll(taskKey: String) {
        activeRuns.values.filter { it.definition.key == taskKey && !it.cancelRequested }.forEach {
            it.cancelRequested = true
            emit(it, force = true)
        }
    }

    /** Called when runs have been deleted from the history */
    fun forget(runIds: Collection<String>) {
        runIds.forEach { recentRuns.remove(it) }
    }

    fun toDto(entry: TaskRunEntry): TaskRunDto {
        activeRuns[entry.id]?.let { return it.toDto() }
        val definition = registry.get(entry.taskKey)
        return TaskRunDto(
            id = entry.id,
            taskKey = entry.taskKey,
            taskName = definition?.name ?: entry.taskKey,
            displayName = entry.displayName,
            link = entry.link,
            trigger = entry.trigger,
            status = entry.status,
            step = null,
            startedAt = entry.startedAt,
            finishedAt = entry.finishedAt,
            summary = entry.summary,
            errorMessage = entry.errorMessage,
            cancellable = false,
            cancelRequested = false,
            visibleInOverview = definition?.visibleInOverview ?: true
        )
    }

    private fun begin(
        taskKey: String,
        trigger: TaskTrigger,
        displayName: String?,
        link: String?,
        cancellable: Boolean?,
        exclusive: Boolean,
        parameters: Map<String, Serializable?> = emptyMap()
    ): ActiveRun {
        val definition = registry.require(taskKey)
        val run = ActiveRun(
            definition = definition,
            trigger = trigger,
            displayName = displayName ?: definition.name,
            link = link,
            cancellable = cancellable ?: definition.cancellable,
            parameters = parameters
        )
        synchronized(startLock) {
            if (exclusive && isRunning(taskKey)) throw TaskAlreadyRunningException(definition.name)
            activeRuns[run.id] = run
        }
        try {
            runRepository.save(
                TaskRunEntry(
                    id = run.id,
                    taskKey = taskKey,
                    displayName = run.displayName.take(1000),
                    link = link?.take(1000),
                    trigger = trigger,
                    status = TaskRunStatus.IN_PROGRESS,
                    startedAt = run.startedAt
                )
            )
        } catch (e: Exception) {
            activeRuns.remove(run.id)
            throw e
        }
        emit(run, force = true)
        return run
    }

    private fun execute(run: ActiveRun, body: (TaskContext) -> TaskResult?, rethrow: Boolean) {
        MdcPropagation.withTaskRun(run.id) {
            log.info { "Started ${run.displayName} (triggered by ${run.trigger.name.lowercase()})" }
            logParameters(run)
            try {
                executeTask(run, body, rethrow)
            } finally {
                // Closes the log file of the run
                finalizeLogger.info(ClassicConstants.FINALIZE_SESSION_MARKER, "End of log")
            }
        }
    }

    private fun logParameters(run: ActiveRun) {
        run.parameters.filterValues { it != null }.takeIf { it.isNotEmpty() }?.let { parameters ->
            log.info { "Parameters: " + parameters.entries.joinToString(", ") { "${it.key}=${it.value}" } }
        }
    }

    private fun executeTask(run: ActiveRun, body: (TaskContext) -> TaskResult?, rethrow: Boolean) {
        try {
            run.throwIfCancelled()
            val result = body(run)
            if (run.cancelRequested) {
                finish(run, TaskRunStatus.CANCELLED, result?.summary, null)
                log.info { "Cancelled ${run.displayName} (task returned without throwing)" }
            } else {
                finish(run, TaskRunStatus.COMPLETED, result?.summary, null)
                log.info { "Finished ${run.displayName}" + (result?.summary?.let { ": $it" } ?: "") }
            }
        } catch (e: TaskCancelledException) {
            finish(run, TaskRunStatus.CANCELLED, e.message, null)
            log.info { "Cancelled ${run.displayName}" }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            finish(run, TaskRunStatus.CANCELLED, null, "Interrupted")
            log.info { "Interrupted ${run.displayName}" }
            if (rethrow) throw e
        } catch (e: Throwable) {
            if (run.cancelRequested && e.cause is InterruptedException) {
                finish(run, TaskRunStatus.CANCELLED, null, null)
            } else {
                finish(run, TaskRunStatus.FAILED, null, e.message ?: e.javaClass.simpleName)
            }
            log.error(e) { "${run.displayName} failed: ${e.message}" }
            if (rethrow) throw e
        }
    }

    private fun finish(run: ActiveRun, status: TaskRunStatus, summary: String?, errorMessage: String?) {
        run.status = status
        run.finishedAt = Instant.now()
        run.summary = summary?.take(MAX_TEXT_LENGTH)
        run.errorMessage = errorMessage?.take(MAX_TEXT_LENGTH)
        try {
            runRepository.findById(run.id).ifPresent {
                it.status = status
                it.finishedAt = run.finishedAt
                it.summary = run.summary
                it.errorMessage = run.errorMessage
                it.displayName = run.displayName.take(1000)
                runRepository.save(it)
            }
        } catch (e: Exception) {
            log.warn { "Could not persist the result of ${run.displayName}: ${e.message}" }
        }
        val dto = run.toDto()
        activeRuns.remove(run.id)
        recentRuns[run.id] = dto
        evictStaleRecentRuns()
        publish(dto)
    }

    private fun evictStaleRecentRuns() {
        val cutoff = Instant.now().minus(RECENT_RUNS_RETENTION)
        recentRuns.values.removeIf { it.finishedAt?.isBefore(cutoff) == true }
        if (recentRuns.size > RECENT_RUNS_LIMIT) {
            recentRuns.values.sortedBy { it.startedAt }.take(recentRuns.size - RECENT_RUNS_LIMIT)
                .forEach { recentRuns.remove(it.id) }
        }
    }

    private fun emit(run: ActiveRun, force: Boolean) {
        val now = System.nanoTime()
        if (!force && now - run.lastEmittedAt < minEmitInterval.toNanos()) return
        run.lastEmittedAt = now
        publish(run.toDto())
    }

    private fun publish(dto: TaskRunDto) {
        // Emissions may come from different threads, but the sink requires serialized calls
        synchronized(events) {
            events.tryEmitNext(dto)
        }
    }

    private inner class ActiveRun(
        val definition: TaskDefinition,
        override val trigger: TaskTrigger,
        @Volatile var displayName: String,
        val link: String?,
        val cancellable: Boolean,
        val parameters: Map<String, Serializable?>
    ) : TaskContext {
        val id: String = UUID.randomUUID().toString()
        val startedAt: Instant = Instant.now()

        @Volatile
        var cancelRequested = false

        @Volatile
        var step: TaskStepDto? = null

        @Volatile
        var lastEmittedAt = 0L

        @Volatile
        var status = TaskRunStatus.IN_PROGRESS
        var finishedAt: Instant? = null
        var summary: String? = null
        var errorMessage: String? = null

        override val runId: UUID = UUID.fromString(id)

        override val isCancellationRequested: Boolean
            get() = cancelRequested

        override fun progress(description: String, current: Int?, total: Int?) {
            val previous = step
            step = TaskStepDto(description, current, total)
            emit(this, force = previous?.description != description || (current != null && current == total))
        }

        override fun <T : Serializable> setting(key: String): T? = settingsService.get(definition, key)

        @Suppress("UNCHECKED_CAST")
        override fun <T : Serializable> parameter(key: String): T? {
            require(definition.parameters.any { it.key == key }) {
                "Unknown parameter '$key' of task ${definition.key}"
            }
            return parameters[key] as T?
        }

        override fun wrap(runnable: Runnable): Runnable = MdcPropagation.wrap {
            throwIfCancelled()
            runnable.run()
        }

        override fun <T> wrap(callable: Callable<T>): Callable<T> = MdcPropagation.wrap(Callable {
            throwIfCancelled()
            callable.call()
        })

        fun toDto() = TaskRunDto(
            id = id,
            taskKey = definition.key,
            taskName = definition.name,
            displayName = displayName,
            link = link,
            trigger = trigger,
            status = status,
            step = if (status == TaskRunStatus.IN_PROGRESS) step else null,
            startedAt = startedAt,
            finishedAt = finishedAt,
            summary = summary,
            errorMessage = errorMessage,
            cancellable = cancellable && status == TaskRunStatus.IN_PROGRESS,
            cancelRequested = cancelRequested,
            visibleInOverview = definition.visibleInOverview
        )
    }
}

class TaskAlreadyRunningException(taskName: String) : IllegalStateException("$taskName is already running")
