package org.gameyfin.app.core.backup

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.*

/**
 * Manages the files in the backup directory:
 * - the backup archives themselves
 * - a marker file for a restore which should be executed on the next startup
 * - a status file with the result of the last restore
 * - a marker file indicating that missing images should be restored after a restore (see [ImageRecoveryService])
 */
class BackupStorage(directory: Path) {

    companion object {
        private val VALID_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,200}\\.zip$")
        private val TIMESTAMP_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
        private const val PENDING_RESTORE_FILE = ".pending-restore"
        private const val RESTORE_STATUS_FILE = ".last-restore.json"
        private const val PENDING_IMAGE_RECOVERY_FILE = ".pending-image-recovery"
        private const val TEMP_PREFIX = ".tmp-"
    }

    val root: Path = directory.toAbsolutePath().normalize()

    private val pendingRestoreFile: Path get() = root.resolve(PENDING_RESTORE_FILE)
    private val restoreStatusFile: Path get() = root.resolve(RESTORE_STATUS_FILE)
    private val pendingImageRecoveryFile: Path get() = root.resolve(PENDING_IMAGE_RECOVERY_FILE)

    fun ensureExists() {
        root.createDirectories()
    }

    fun isValidName(name: String) = VALID_NAME.matches(name)

    fun newBackupName(createdAt: Instant, suffix: String? = null): String {
        val base = "gameyfin-backup-${TIMESTAMP_FORMAT.format(createdAt)}${suffix?.let { "-$it" } ?: ""}"
        var name = "$base${BackupFormat.FILE_EXTENSION}"
        var counter = 1
        while (root.resolve(name).exists()) {
            name = "$base-${counter++}${BackupFormat.FILE_EXTENSION}"
        }
        return name
    }

    /**
     * Resolves the backup with the given name.
     *
     * @throws IllegalArgumentException if the name is invalid or the backup does not exist
     */
    fun resolve(name: String): Path {
        require(isValidName(name)) { "Invalid backup name" }
        // Pick the path from the directory listing instead of building it from user input
        return requireNotNull(list().firstOrNull { it.name == name }) { "Invalid backup name" }
    }

    fun list(): List<Path> {
        if (!root.isDirectory()) return emptyList()
        return root.listDirectoryEntries("*${BackupFormat.FILE_EXTENSION}")
            .filter { it.isRegularFile() && isValidName(it.name) }
    }

    fun delete(name: String) {
        resolve(name).deleteExisting()
    }

    fun createTempFile(): Path {
        ensureExists()
        return Files.createTempFile(root, TEMP_PREFIX, ".zip")
    }

    /** Atomically moves a fully written temporary file to its final location */
    fun publish(tempFile: Path, name: String): Path {
        require(isValidName(name)) { "Invalid backup name" }
        val target = root.resolve(name)
        try {
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tempFile, target)
        }
        return target
    }

    /** Removes leftovers of interrupted backup operations */
    fun cleanupTempFiles() {
        if (!root.isDirectory()) return
        root.listDirectoryEntries("$TEMP_PREFIX*").forEach { it.deleteIfExists() }
    }

    fun schedulePendingRestore(name: String) {
        resolve(name)
        ensureExists()
        pendingRestoreFile.writeText(name)
    }

    fun pendingRestore(): String? =
        pendingRestoreFile.takeIf { it.isRegularFile() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }

    fun clearPendingRestore() {
        pendingRestoreFile.deleteIfExists()
    }

    fun scheduleImageRecovery() {
        ensureExists()
        pendingImageRecoveryFile.writeText(Instant.now().toString())
    }

    fun isImageRecoveryPending(): Boolean = pendingImageRecoveryFile.isRegularFile()

    fun clearPendingImageRecovery() {
        pendingImageRecoveryFile.deleteIfExists()
    }

    fun writeRestoreStatus(status: RestoreStatusDto) {
        ensureExists()
        val node = BackupFormat.jsonMapper.createObjectNode()
            .put("backupName", status.backupName)
            .put("success", status.success)
            .put("message", status.message)
            .put("finishedAt", status.finishedAt.toString())
        restoreStatusFile.writeText(BackupFormat.jsonMapper.writeValueAsString(node))
    }

    fun readRestoreStatus(): RestoreStatusDto? {
        if (!restoreStatusFile.isRegularFile()) return null
        return try {
            val node = BackupFormat.jsonMapper.readTree(restoreStatusFile.readText())
            RestoreStatusDto(
                backupName = node.get("backupName").asString(),
                success = node.get("success").asBoolean(),
                message = node.get("message").asString(),
                finishedAt = Instant.parse(node.get("finishedAt").asString())
            )
        } catch (_: Exception) {
            null
        }
    }
}
