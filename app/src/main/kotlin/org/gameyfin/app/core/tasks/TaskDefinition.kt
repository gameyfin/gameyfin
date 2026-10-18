package org.gameyfin.app.core.tasks

import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.gameyfin.pluginapi.tasks.TaskContext
import org.gameyfin.pluginapi.tasks.TaskResult
import org.gameyfin.pluginapi.tasks.TaskTrigger

enum class TaskRunStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class TaskSource {
    CORE,
    PLUGIN
}

/**
 * Definition of a task which is managed by the [TaskRegistry].
 *
 * A task is either executed by the task framework itself ([TaskAction.Run]), in which case every execution
 * creates exactly one run, or it dispatches the work itself ([TaskAction.Dispatch]), e.g. library scans, which
 * create one run per library using [TaskRunService.runBlocking].
 */
class TaskDefinition(
    val key: String,
    val name: String,
    val description: String = "",
    val source: TaskSource = TaskSource.CORE,
    val pluginId: String? = null,
    /** Default cron expression, or `null` if the task is not scheduled by default */
    val defaultSchedule: String? = null,
    val scheduledByDefault: Boolean = defaultSchedule != null,
    /** Whether the task can be scheduled at all */
    val schedulable: Boolean = true,
    /** Whether the task can be started manually by an administrator */
    val manuallyTriggerable: Boolean = true,
    /** Whether runs of this task can be cancelled (cooperatively) */
    val cancellable: Boolean = true,
    val settings: List<ConfigMetadata<*>> = emptyList(),
    val parameters: List<ConfigMetadata<*>> = emptyList(),
    val action: TaskAction
)

sealed interface TaskAction {
    /** Every execution of the task is a single run, executed by the task framework */
    class Run(val execute: (TaskContext) -> TaskResult?) : TaskAction

    /** The task creates its runs itself, e.g. one per library */
    class Dispatch(val dispatch: (TaskTrigger) -> Unit) : TaskAction
}
