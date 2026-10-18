package org.gameyfin.app.core.tasks

import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.core.tasks.logging.TaskRunLogs
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

/**
 * Download of the complete log files of task runs, which is not supported by Hilla endpoints.
 */
@RestController
@RequestMapping("/task-runs")
class TaskRunLogController(private val runLogs: TaskRunLogs) {

    @GetMapping("/{runId}/log")
    fun downloadLog(@PathVariable runId: String): ResponseEntity<Resource> {
        if (!isCurrentUserAdmin()) throw ResponseStatusException(HttpStatus.FORBIDDEN)
        val file = try {
            runLogs.file(runId)
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid task run ID")
        }
        if (!runLogs.exists(runId)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "No log available")

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("task-run-$runId.log").build().toString()
            )
            .body(FileSystemResource(file))
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(e: ResponseStatusException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(e.statusCode).body(mapOf("message" to (e.reason ?: e.message)))
}
