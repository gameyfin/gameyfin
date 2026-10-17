package org.gameyfin.pluginapi.tasks

import java.io.Serializable
import java.util.*
import java.util.concurrent.Callable

/**
 * Context of a single run of a task.
 */
interface TaskContext {

    /**
     * Unique ID of the current run.
     */
    val runId: UUID

    /**
     * What caused the current run.
     */
    val trigger: TaskTrigger

    /**
     * Whether an administrator has requested to cancel the current run.
     * Long-running tasks should check this regularly and stop as soon as possible.
     */
    val isCancellationRequested: Boolean

    /**
     * Throws a [TaskCancelledException] if the cancellation of the current run has been requested.
     */
    fun throwIfCancelled() {
        if (isCancellationRequested) throw TaskCancelledException()
    }

    /**
     * Reports the progress of the current run.
     *
     * @param description Description of the current step, e.g. "Downloading images".
     * @param current Number of items of the current step which have been processed, if known.
     * @param total Total number of items of the current step, if known.
     */
    fun progress(description: String, current: Int? = null, total: Int? = null)

    /**
     * Returns the configured value of a setting declared in [GameyfinTask.settings],
     * or its default value if it has not been configured.
     */
    fun <T : Serializable> setting(key: String): T?

    /**
     * Wraps a [Runnable] which is executed on a different thread, so everything it logs is included in the log
     * of the current run.
     */
    fun wrap(runnable: Runnable): Runnable

    /**
     * Wraps a [Callable] which is executed on a different thread, so everything it logs is included in the log
     * of the current run.
     */
    fun <T> wrap(callable: Callable<T>): Callable<T>
}

/**
 * What caused a run of a task.
 */
enum class TaskTrigger {
    /** The run has been started by the schedule of the task */
    SCHEDULE,

    /** The run has been started by an administrator */
    MANUAL,

    /** The run has been started by Gameyfin itself, e.g. as part of another operation */
    SYSTEM,

    /** The run has been started because of changes on the file system */
    WATCHER
}

/**
 * Result of a run of a task.
 *
 * @property summary Short summary of the result, shown to administrators.
 */
data class TaskResult(val summary: String? = null)

/**
 * Thrown to stop a run of a task after its cancellation has been requested.
 *
 * @param message Optional message describing the state in which the run has been cancelled.
 */
class TaskCancelledException(message: String? = null) : RuntimeException(message)
