package org.gameyfin.app.core.tasks

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.core.tasks.dto.TaskDto
import org.gameyfin.app.core.tasks.dto.TaskRunDto
import org.gameyfin.app.core.tasks.dto.TaskRunPageDto
import org.gameyfin.app.core.tasks.dto.TaskUpdateDto
import reactor.core.publisher.Flux

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class TaskEndpoint(
    private val taskService: TaskService,
    private val taskRunService: TaskRunService
) {

    fun getTasks(): List<TaskDto> = taskService.getTasks()

    fun updateTask(update: TaskUpdateDto): TaskDto = taskService.updateTask(update)

    fun runNow(taskKey: String, parameters: Map<String, String?>?): TaskRunDto? =
        taskService.runNow(taskKey, parameters.orEmpty())

    fun cancelRun(runId: String) = taskService.cancelRun(runId)

    fun getRuns(taskKey: String?, page: Int, size: Int): TaskRunPageDto = taskService.getRuns(taskKey, page, size)

    fun getRun(runId: String): TaskRunDto? = taskService.getRun(runId)

    fun getRunLog(runId: String): String? = taskService.getRunLog(runId)

    /**
     * Streams updates of active and recently finished task runs.
     * The first emission contains all active and recently finished runs.
     */
    @PermitAll
    fun subscribeToTaskRuns(): Flux<List<TaskRunDto>> {
        return if (isCurrentUserAdmin()) taskRunService.subscribe()
        else Flux.empty()
    }
}
