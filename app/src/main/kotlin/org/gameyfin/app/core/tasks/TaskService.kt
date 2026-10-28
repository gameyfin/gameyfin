package org.gameyfin.app.core.tasks

import org.gameyfin.app.core.tasks.dto.TaskDto
import org.gameyfin.app.core.tasks.dto.TaskRunDto
import org.gameyfin.app.core.tasks.dto.TaskRunPageDto
import org.gameyfin.app.core.tasks.dto.TaskUpdateDto
import org.gameyfin.app.core.tasks.logging.TaskRunLogs
import org.gameyfin.app.core.tasks.persistence.TaskRunRepository
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Entry point for the management of tasks by administrators.
 */
@Service
class TaskService(
    private val registry: TaskRegistry,
    private val runService: TaskRunService,
    private val schedulerService: TaskSchedulerService,
    private val settingsService: TaskSettingsService,
    private val runRepository: TaskRunRepository,
    private val runLogs: TaskRunLogs,
    private val pluginTaskRegistrar: PluginTaskRegistrar
) {

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }

    fun getTasks(): List<TaskDto> =
        registry.getAll()
            .sortedWith(compareBy<TaskDefinition> { it.source }.thenBy { it.name.lowercase() })
            .map { toDto(it) }

    fun getTask(key: String): TaskDto = toDto(registry.require(key))

    @Transactional
    fun updateTask(update: TaskUpdateDto): TaskDto {
        val definition = registry.require(update.key)

        update.settings?.let { settings ->
            val errors = settingsService.validate(definition, settings)
            require(errors.isEmpty()) { errors.entries.joinToString("; ") { "${it.key}: ${it.value}" } }
        }

        if (definition.schedulable) {
            schedulerService.updateSchedule(definition, update.schedule, update.scheduleEnabled)
        }
        update.settings?.let { settingsService.update(definition, it) }

        return toDto(definition)
    }

    /**
     * Starts the task immediately.
     * @param parameters raw values of the parameters declared by the task
     * @return the started run, or `null` if the task creates its runs itself (e.g. library scans)
     */
    fun runNow(key: String, parameters: Map<String, String?> = emptyMap()): TaskRunDto? {
        val definition = registry.require(key)
        require(definition.manuallyTriggerable) { "${definition.name} can not be started manually" }
        return runService.trigger(key, TaskTrigger.MANUAL, parameters)
    }

    fun cancelRun(runId: String) {
        val run = runService.getActiveRun(runId) ?: throw IllegalArgumentException("Task run is not running")
        require(run.cancellable) { "${run.taskName} can not be cancelled" }
        runService.cancel(runId)
    }

    fun getRuns(taskKey: String?, page: Int, size: Int): TaskRunPageDto {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        val slice = if (taskKey != null) runRepository.findAllByTaskKeyOrderByStartedAtDesc(taskKey, pageable)
        else runRepository.findAllByOrderByStartedAtDesc(pageable)
        return TaskRunPageDto(runs = slice.content.map { runService.toDto(it) }, hasMore = slice.hasNext())
    }

    fun getRun(runId: String): TaskRunDto? =
        runService.getActiveRun(runId) ?: runRepository.findById(runId).orElse(null)?.let { runService.toDto(it) }

    fun getRunLog(runId: String): String? = runLogs.read(runId)

    private fun toDto(definition: TaskDefinition): TaskDto {
        val schedule = schedulerService.getSchedule(definition)
        val lastRun = runRepository.findFirstByTaskKeyOrderByStartedAtDesc(definition.key)?.let { runService.toDto(it) }
        return TaskDto(
            key = definition.key,
            name = definition.name,
            description = definition.description,
            source = definition.source,
            pluginId = definition.pluginId,
            pluginName = definition.pluginId?.let { pluginTaskRegistrar.pluginName(it) },
            schedulable = definition.schedulable,
            manuallyTriggerable = definition.manuallyTriggerable,
            cancellable = definition.cancellable,
            schedule = schedule.cron,
            scheduleEnabled = schedule.enabled,
            defaultSchedule = definition.defaultSchedule,
            nextExecution = schedulerService.nextExecution(definition),
            settingsMetadata = definition.settings.map { TaskSettingsService.toDto(it) },
            settings = settingsService.getRaw(definition),
            parametersMetadata = definition.parameters.map { TaskSettingsService.toDto(it) },
            lastRun = lastRun,
            running = runService.isRunning(definition.key)
        )
    }
}
