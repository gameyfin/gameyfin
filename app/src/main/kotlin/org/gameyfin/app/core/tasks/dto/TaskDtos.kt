package org.gameyfin.app.core.tasks.dto

import org.gameyfin.app.core.plugins.dto.PluginConfigMetadataDto
import org.gameyfin.app.core.tasks.TaskRunStatus
import org.gameyfin.app.core.tasks.TaskSource
import org.gameyfin.pluginapi.tasks.TaskTrigger
import java.time.Instant

data class TaskDto(
    val key: String,
    val name: String,
    val description: String,
    val source: TaskSource,
    val pluginId: String?,
    val pluginName: String?,
    val schedulable: Boolean,
    val manuallyTriggerable: Boolean,
    val cancellable: Boolean,
    /** Currently configured cron expression */
    val schedule: String?,
    val scheduleEnabled: Boolean,
    val defaultSchedule: String?,
    val nextExecution: Instant?,
    val settingsMetadata: List<PluginConfigMetadataDto>,
    val settings: Map<String, String?>,
    /** Parameters which can be specified when starting the task manually */
    val parametersMetadata: List<PluginConfigMetadataDto>,
    val lastRun: TaskRunDto?,
    val running: Boolean
)

data class TaskUpdateDto(
    val key: String,
    val schedule: String?,
    val scheduleEnabled: Boolean,
    val settings: Map<String, String?>?
)

data class TaskRunDto(
    val id: String,
    val taskKey: String,
    val taskName: String,
    val displayName: String,
    /** Link to the page showing the subject of the run (e.g. a library), if any */
    val link: String?,
    val trigger: TaskTrigger,
    val status: TaskRunStatus,
    val step: TaskStepDto?,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val summary: String?,
    val errorMessage: String?,
    val cancellable: Boolean,
    val cancelRequested: Boolean,
    val visibleInOverview: Boolean = true
)

data class TaskStepDto(
    val description: String,
    val current: Int? = null,
    val total: Int? = null
)

data class TaskRunPageDto(
    val runs: List<TaskRunDto>,
    val hasMore: Boolean
)
