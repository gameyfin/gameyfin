package org.gameyfin.app.core.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.setup.SetupService
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import kotlin.io.path.fileSize

/**
 * Binary transfer of backup archives, which is not supported by Hilla endpoints.
 * All other backup operations are available via [BackupEndpoint].
 */
@RestController
@RequestMapping("/backups")
class BackupController(
    private val backupService: BackupService,
    private val setupService: SetupService
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @GetMapping("/{name}")
    fun downloadBackup(@PathVariable name: String): ResponseEntity<Resource> {
        requireAdmin()
        val file = backupService.getBackupFile(name)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .contentLength(file.fileSize())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.fileName.toString()).build().toString()
            )
            .body(FileSystemResource(file))
    }

    /**
     * Uploads a backup archive (sent as raw request body) into the backup directory.
     */
    @PostMapping("/upload")
    fun uploadBackup(request: HttpServletRequest): BackupDto {
        requireAdmin()
        return backupService.storeUploadedBackup(request.inputStream)
    }

    /**
     * Uploads a backup archive (sent as raw request body) and restores it.
     * Only available as long as the initial setup has not been completed.
     */
    @PostMapping("/setup/restore")
    fun restoreDuringSetup(request: HttpServletRequest): ResponseEntity<Void> {
        if (setupService.isSetupCompleted()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Setup has already been completed")
        }

        val backup = backupService.storeUploadedBackup(request.inputStream)
        if (!backup.restorable) {
            backupService.deleteBackup(backup.name)
            throw InvalidBackupException(backup.problem ?: "The backup can not be restored")
        }

        backupService.restoreBackup(backup.name, createSafetyBackup = false)
        return ResponseEntity.accepted().build()
    }

    @ExceptionHandler(InvalidBackupException::class, IllegalArgumentException::class)
    fun handleBadRequest(e: Exception): ResponseEntity<Map<String, String?>> =
        ResponseEntity.badRequest().body(mapOf("message" to e.message))

    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(e: Exception): ResponseEntity<Map<String, String?>> {
        log.warn { "Backup operation failed: ${e.message}" }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("message" to e.message))
    }

    /**
     * Answers directly instead of forwarding to the error page, which is not accessible for anonymous users.
     */
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(e: ResponseStatusException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(e.statusCode).body(mapOf("message" to (e.reason ?: e.message)))

    private fun requireAdmin() {
        if (!isCurrentUserAdmin()) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }
}
