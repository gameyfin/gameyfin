package org.gameyfin.pluginapi.tasks

import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.pf4j.ExtensionPoint

/**
 * Extension point for background tasks contributed by plugins.
 *
 * Tasks are registered as soon as the plugin has been started and are removed again once it is stopped.
 * They are managed by administrators on the "Tasks" page, alongside the tasks of Gameyfin itself:
 * the schedule can be changed, the task can be started manually, running tasks can be cancelled and the log
 * of every run can be inspected.
 *
 * Everything that is logged (using any SLF4J/kotlin-logging logger) on the thread executing [execute] is written
 * to the log of the current run. Work executed on other threads is only included if it is wrapped using
 * [TaskContext.wrap].
 */
interface GameyfinTask : ExtensionPoint {

    /**
     * Key of the task, must be unique within the plugin and must not change between versions,
     * as the schedule and settings are stored using it.
     */
    val key: String

    /**
     * Human-readable name of the task.
     */
    val name: String

    /**
     * Short description of what the task does.
     */
    val description: String
        get() = ""

    /**
     * Default schedule as [Spring cron expression](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-cron-expression)
     * (e.g. `0 0 4 * * *` or `@daily`), or `null` if the task should only be started manually by default.
     * Administrators can change the schedule at any time.
     */
    val defaultSchedule: String?
        get() = null

    /**
     * Whether the [defaultSchedule] is active by default.
     */
    val scheduledByDefault: Boolean
        get() = defaultSchedule != null

    /**
     * Settings of the task, which can be changed by administrators.
     * Use [TaskContext.setting] to access the configured values.
     */
    val settings: List<ConfigMetadata<*>>
        get() = emptyList()

    /**
     * Parameters of a single run, which administrators can enter when starting the task manually.
     * Unlike [settings], they are not persisted: scheduled runs always use the default values.
     * Use [TaskContext.parameter] to access the values of the current run.
     * Use [GameSelection] as type to let administrators pick games.
     */
    val parameters: List<ConfigMetadata<*>>
        get() = emptyList()

    /**
     * Whether a run of this task can be cancelled.
     * Cancellation is cooperative, so the task must check [TaskContext.isCancellationRequested] regularly.
     */
    val cancellable: Boolean
        get() = true

    /**
     * Executes the task. Only one run of a task is executed at a time.
     *
     * Throwing an exception marks the run as failed, throwing a [TaskCancelledException] marks it as cancelled.
     *
     * @param context Context of the current run, used to report progress, read settings and check for cancellation.
     * @return The result of the run, shown to administrators, or `null` if there is nothing to report.
     */
    fun execute(context: TaskContext): TaskResult?
}
