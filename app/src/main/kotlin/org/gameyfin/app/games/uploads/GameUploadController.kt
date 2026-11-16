package org.gameyfin.app.games.uploads

import jakarta.servlet.http.HttpServletRequest
import org.gameyfin.app.core.plugins.dto.ExternalProviderIdDto
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.games.dto.GameUpdateDto
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartHttpServletRequest
import org.springframework.web.server.ResponseStatusException
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.util.*

@RestController
@RequestMapping("/game-uploads")
class GameUploadController(private val uploads: GameUploadService) {
    @PostMapping("/destination", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun validateDestination(@RequestBody request: GameUploadDestinationDto) {
        owner()
        uploads.validateDestination(request)
    }

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun create(@RequestBody request: GameUploadRequest) = uploads.create(owner(), request)

    @PostMapping("/{id}/metadata", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun prepare(@PathVariable id: UUID, @RequestBody originalIds: Map<String, ExternalProviderIdDto>) =
        uploads.prepare(owner(), id, originalIds)

    @PostMapping("/{id}/restart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun restart(@PathVariable id: UUID) = uploads.restart(owner(), id)

    @PutMapping("/{id}/files/{index}", consumes = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun transfer(@PathVariable id: UUID, @PathVariable index: Int, request: HttpServletRequest) {
        val owner = owner()
        request.inputStream.use { uploads.transfer(owner, id, index, it) }
    }

    @PostMapping("/{id}/complete", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun complete(
        @PathVariable id: UUID,
        @RequestPart("metadata") metadata: GameUpdateDto,
        request: MultipartHttpServletRequest
    ): GameUploadStatus {
        val owner = owner()
        val files = request.multiFileMap.filterKeys { it != "metadata" }
        require(files.values.all { it.size == 1 }) { "Upload keys must be unique" }
        return uploads.complete(owner, id, metadata, files.mapValues { it.value.single() })
    }

    @GetMapping("/{id}")
    fun status(@PathVariable id: UUID) = uploads.status(owner(), id)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(@PathVariable id: UUID) = uploads.cancel(owner(), id)

    @ExceptionHandler(IllegalArgumentException::class, ArithmeticException::class)
    fun invalid(e: Exception): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Invalid upload")))

    @ExceptionHandler(FileAlreadyExistsException::class)
    fun conflict(): ResponseEntity<Map<String, String>> = ResponseEntity.status(HttpStatus.CONFLICT)
        .body(mapOf("message" to "A file or folder with this name already exists"))

    @ExceptionHandler(IOException::class)
    fun storage(): ResponseEntity<Map<String, String>> = ResponseEntity.status(HttpStatus.INSUFFICIENT_STORAGE)
        .body(mapOf("message" to "Upload could not be stored. Check available disk space and directory permissions."))

    @ExceptionHandler(ResponseStatusException::class)
    fun failure(e: ResponseStatusException): ResponseEntity<Map<String, String>> = ResponseEntity.status(e.statusCode)
        .body(mapOf("message" to (e.reason ?: "Upload failed")))

    private fun owner(): String {
        if (!isCurrentUserAdmin()) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required")
        return getCurrentAuth()!!.name
    }
}
