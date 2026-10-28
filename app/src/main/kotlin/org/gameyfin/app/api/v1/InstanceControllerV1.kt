package org.gameyfin.app.api.v1

import io.swagger.v3.oas.annotations.Operation
import org.gameyfin.app.api.security.ApiAccess
import org.gameyfin.app.core.tasks.TaskService
import org.gameyfin.app.core.tasks.persistence.TaskRunEntry
import org.gameyfin.app.core.tasks.persistence.TaskRunRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.info.BuildProperties
import org.springframework.core.env.Environment
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1")
class InstanceControllerV1(private val access: ApiAccess, private val environment: Environment,
    private val build: ObjectProvider<BuildProperties>, private val health: HealthEndpoint,
    private val tasks: TaskService, private val runs: TaskRunRepository) {

    @GetMapping("/instance")
    @Operation(operationId = "getInstance", description = "Requires instance:read. Missing development build metadata is null.")
    fun instance(): InstanceV1 {
        access.require("instance:read")
        val properties = build.ifAvailable
        return InstanceV1(properties?.version ?: environment.getProperty("spring.application.version", "unknown"),
            properties?.time, properties?.get("git.commit")?.takeUnless { it.isBlank() || it == "unknown" })
    }

    @GetMapping("/health")
    @Operation(operationId = "getHealth", description = "Requires instance:read. Aggregate health only; HTTP 503 when not healthy.")
    fun health(): ResponseEntity<HealthV1> {
        access.require("instance:read")
        val status = health.health().status.code
        return ResponseEntity.status(if (status == "UP") 200 else 503).body(HealthV1(status))
    }

    @GetMapping("/tasks")
    @Operation(operationId = "listTasks", description = "Requires tasks:read or an administrator personal token. Does not expose task settings, parameters, or secrets.")
    fun tasks(): List<TaskV1> {
        access.require("tasks:read", true)
        return tasks.getTasks().map { TaskV1(it.key, it.name, it.description, it.running) }
    }

    @GetMapping("/task-runs")
    @Operation(operationId = "listTaskRuns", description = "Requires tasks:read or an administrator personal token. sort: id, startedAt or finishedAt. Raw logs and error details are excluded.")
    fun runs(@RequestParam(required = false) taskId: String?, @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int, @RequestParam(defaultValue = "startedAt,desc") sort: String): PageV1<TaskRunV1> {
        access.require("tasks:read", true)
        val specification = Specification<TaskRunEntry> { root, _, cb -> taskId?.let { cb.equal(root.get<String>("taskKey"), it) } ?: cb.conjunction() }
        return runs.findAll(specification, apiPage(page, size, sort, setOf("id", "startedAt", "finishedAt"))).publicPage {
            TaskRunV1(it.id, it.taskKey, it.status.name, it.startedAt, it.finishedAt)
        }
    }

    @GetMapping("/task-runs/{id}")
    @Operation(operationId = "getTaskRun", description = "Requires tasks:read or an administrator personal token.")
    fun run(@PathVariable id: String): TaskRunV1 {
        access.require("tasks:read", true)
        val run = tasks.getRun(id) ?: throw NoSuchElementException("Task run not found")
        return TaskRunV1(run.id, run.taskKey, run.status.name, run.startedAt, run.finishedAt)
    }
}
