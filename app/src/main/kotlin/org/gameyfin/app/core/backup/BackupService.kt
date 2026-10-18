package org.gameyfin.app.core.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import liquibase.integration.spring.SpringResourceAccessor
import org.gameyfin.app.core.tasks.TaskAction
import org.gameyfin.app.core.tasks.TaskDefinition
import org.gameyfin.app.core.tasks.TaskRegistry
import org.gameyfin.app.core.tasks.TaskRunService
import org.gameyfin.app.core.tasks.dto.TaskRunDto
import org.gameyfin.app.system.SystemService
import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.gameyfin.pluginapi.tasks.TaskContext
import org.gameyfin.pluginapi.tasks.TaskResult
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.info.BuildProperties
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.Semaphore
import javax.sql.DataSource
import kotlin.io.path.deleteIfExists
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.name

@Service
class BackupService(
    private val dataSource: DataSource,
    private val systemService: SystemService,
    private val buildProperties: ObjectProvider<BuildProperties>,
    private val taskRegistry: TaskRegistry,
    private val taskRunService: TaskRunService,
    resourceLoader: ResourceLoader,
    @Value($$"${gameyfin.backup.directory:./backups}") backupDirectory: String,
    @Value($$"${spring.content.fs.filesystem-root:./data/}") mediaDirectory: String,
    @Value($$"${spring.liquibase.change-log:classpath:/db/changelog/db.changelog-master.yaml}") changeLog: String,
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val PRE_RESTORE_SUFFIX = "pre-restore"
        private const val UPLOADED_SUFFIX = "uploaded"
        private const val DATABASE_SWITCH_SUFFIX = "database-switch"
        private const val SCHEDULED_SUFFIX = "scheduled"
        private val SCHEDULED_BACKUP_NAME = Regex("^gameyfin-backup-\\d{8}-\\d{6}-$SCHEDULED_SUFFIX(-\\d+)?\\.zip$")

        const val TASK_KEY = "backup"
        const val INCLUDE_MEDIA_SETTING = "includeMedia"
        const val RETENTION_COUNT_SETTING = "retentionCount"
    }

    private val storage = BackupStorage(Path.of(backupDirectory))
    private val mediaRoot: Path = Path.of(mediaDirectory)
    private val exporter = DatabaseExporter()
    private val restorer = DatabaseRestorer(dataSource, changeLog, SpringResourceAccessor(resourceLoader))

    /** Only one backup operation may run at a time; a semaphore is used as backups are created asynchronously */
    private val operationPermit = Semaphore(1)

    @Volatile
    private var restartPending = false

    init {
        try {
            storage.ensureExists()
            storage.cleanupTempFiles()
        } catch (e: Exception) {
            log.warn { "Could not initialize backup directory ${storage.root}: ${e.message}" }
        }
    }

    @PostConstruct
    fun registerTask() {
        taskRegistry.register(
            TaskDefinition(
                key = TASK_KEY,
                name = "Backup",
                description = "Creates a backup of the database and optionally of the media files. " +
                        "Only backups created by this task are deleted according to the retention.",
                defaultSchedule = "0 0 3 * * *",
                scheduledByDefault = false,
                settings = listOf(
                    ConfigMetadata(
                        key = INCLUDE_MEDIA_SETTING,
                        type = Boolean::class.javaObjectType,
                        label = "Include media files",
                        description = "Whether to include media files (cover images, screenshots, etc.)",
                        default = false
                    ),
                    ConfigMetadata(
                        key = RETENTION_COUNT_SETTING,
                        type = Int::class.javaObjectType,
                        label = "Backups to keep",
                        description = "Number of backups created by this task to keep (0 = keep all)",
                        default = 7
                    )
                ),
                action = TaskAction.Run { createScheduledBackup(it) }
            )
        )
    }

    fun getBackups(): List<BackupDto> =
        storage.list().map { toDto(it) }.sortedByDescending { it.createdAt }

    /**
     * Starts the creation of a backup in the background.
     *
     * @return the run of the backup task creating the backup
     */
    fun createBackup(includeMedia: Boolean): TaskRunDto {
        acquireOperationPermit()
        try {
            return taskRunService.start(
                taskKey = TASK_KEY,
                trigger = TaskTrigger.MANUAL,
                displayName = "Backup${if (includeMedia) " (including media)" else ""}"
            ) { context ->
                try {
                    TaskResult(describe(writeBackup(context, includeMedia, suffix = null)))
                } finally {
                    operationPermit.release()
                }
            }
        } catch (e: Exception) {
            operationPermit.release()
            throw e
        }
    }

    /**
     * Creates a backup using the settings of the backup task and deletes old backups created by the task.
     */
    private fun createScheduledBackup(context: TaskContext): TaskResult {
        val includeMedia = context.setting<Boolean>(INCLUDE_MEDIA_SETTING) ?: false
        val retentionCount = context.setting<Int>(RETENTION_COUNT_SETTING) ?: 0
        val path = exclusive { writeBackup(context, includeMedia, SCHEDULED_SUFFIX) }
        val deleted = if (retentionCount > 0) deleteOldScheduledBackups(retentionCount) else 0
        return TaskResult(describe(path) + if (deleted > 0) ", $deleted old backup(s) deleted" else "")
    }

    private fun deleteOldScheduledBackups(retentionCount: Int): Int = exclusive {
        val outdated = storage.list()
            .filter { SCHEDULED_BACKUP_NAME.matches(it.name) }
            .sortedByDescending { it.name }
            .drop(retentionCount)
        outdated.forEach {
            storage.delete(it.name)
            log.info { "Deleted outdated backup ${it.name}" }
        }
        outdated.size
    }

    private fun describe(path: Path) = "${path.name} (${formatSize(path.fileSize())})"

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes / 1024.0
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return "%.1f %s".format(java.util.Locale.ROOT, value, units[unit])
    }

    /**
     * Creates a backup as part of another operation (e.g. before a restore), tracked as run of the backup task.
     */
    private fun writeSystemBackup(displayName: String, suffix: String): Path {
        var path: Path? = null
        taskRunService.runBlocking(
            taskKey = TASK_KEY,
            trigger = TaskTrigger.SYSTEM,
            displayName = displayName,
            cancellable = false
        ) { context ->
            path = writeBackup(context, includeMedia = false, suffix = suffix)
            TaskResult(describe(path))
        }
        return path!!
    }

    fun deleteBackup(name: String) = exclusive {
        storage.delete(name)
        log.info { "Deleted backup $name" }
    }

    fun getBackupFile(name: String): Path = storage.resolve(name)

    /**
     * Stores an uploaded backup archive in the backup directory after verifying that it is a Gameyfin backup.
     */
    fun storeUploadedBackup(input: InputStream): BackupDto = exclusive {
        val tempFile = storage.createTempFile()
        try {
            Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
            val manifest = DatabaseRestorer.readManifest(tempFile)
            val path = storage.publish(tempFile, storage.newBackupName(manifest.createdAt, UPLOADED_SUFFIX))
            log.info { "Stored uploaded backup ${path.name}" }
            toDto(path)
        } catch (e: Exception) {
            tempFile.deleteIfExists()
            throw e
        }
    }

    /**
     * Schedules the restore of the given backup and restarts Gameyfin.
     * The restore itself is executed on startup, before the database migrations run and before any other
     * component accesses the database (see [PendingRestoreExecutor]).
     *
     * @param createSafetyBackup whether to back up the current database before restoring
     */
    fun restoreBackup(name: String, createSafetyBackup: Boolean) {
        exclusive {
            val path = storage.resolve(name)
            restorer.validate(DatabaseRestorer.readManifest(path), AppKeyFingerprint.current())

            if (createSafetyBackup) {
                val safetyBackup = writeSystemBackup("Backup before restoring $name", PRE_RESTORE_SUFFIX)
                log.info { "Created backup ${safetyBackup.name} of the current database before restoring" }
            }

            storage.schedulePendingRestore(name)
            restartPending = true
        }

        log.info { "Restarting Gameyfin to restore backup $name" }
        systemService.restart()
    }

    /**
     * Transfers all data to a different database: creates a backup of the current database, schedules it to be
     * restored on the next startup and restarts Gameyfin.
     *
     * @param switchDatabase called once the backup has been created; changes the database connection settings
     * used after the restart, so the backup is restored into the new database (see [PendingRestoreExecutor])
     */
    fun transferDatabase(switchDatabase: (backupName: String) -> Unit) {
        exclusive {
            val backup = writeSystemBackup("Backup for the database transfer", DATABASE_SWITCH_SUFFIX)
            log.info { "Created backup ${backup.name} to transfer the data to the new database" }

            storage.schedulePendingRestore(backup.name)
            try {
                switchDatabase(backup.name)
            } catch (e: Exception) {
                storage.clearPendingRestore()
                throw e
            }
            restartPending = true
        }

        log.info { "Restarting Gameyfin to switch the database" }
        systemService.restart()
    }

    fun getLastRestoreStatus(): RestoreStatusDto? = storage.readRestoreStatus()

    private fun acquireOperationPermit() {
        check(operationPermit.tryAcquire()) { "Another backup operation is currently in progress" }
        if (restartPending) {
            operationPermit.release()
            throw IllegalStateException("A restore is pending, Gameyfin is restarting")
        }
    }

    private fun <T> exclusive(action: () -> T): T {
        acquireOperationPermit()
        try {
            return action()
        } finally {
            operationPermit.release()
        }
    }

    private fun writeBackup(context: TaskContext, includeMedia: Boolean, suffix: String?): Path {
        val createdAt = Instant.now()
        log.info { "Creating backup (media files ${if (includeMedia) "included" else "excluded"})" }
        context.progress("Preparing backup")

        val tempFile = storage.createTempFile()
        try {
            Files.newOutputStream(tempFile).use { output ->
                exporter.writeArchive(
                    dataSource = dataSource,
                    output = output,
                    createdAt = createdAt,
                    appKeyFingerprint = AppKeyFingerprint.current(),
                    gameyfinVersion = buildProperties.ifAvailable?.version,
                    mediaRoot = if (includeMedia) mediaRoot else null,
                    progress = { step ->
                        context.throwIfCancelled()
                        context.progress(step.description, step.current, step.total)
                    }
                )
            }
            context.throwIfCancelled()

            val path = storage.publish(tempFile, storage.newBackupName(createdAt, suffix))
            log.info { "Created backup ${path.name}" }
            return path
        } catch (e: Exception) {
            tempFile.deleteIfExists()
            throw e
        }
    }

    private fun toDto(path: Path): BackupDto {
        val size = path.fileSize()
        return try {
            val manifest = DatabaseRestorer.readManifest(path)
            val problem = try {
                restorer.validate(manifest, AppKeyFingerprint.current())
                null
            } catch (e: InvalidBackupException) {
                e.message
            }
            BackupDto(
                name = path.name,
                sizeBytes = size,
                createdAt = manifest.createdAt,
                gameyfinVersion = manifest.gameyfinVersion,
                databaseProduct = manifest.databaseProduct,
                includesMedia = manifest.includesMedia,
                restorable = problem == null,
                problem = problem
            )
        } catch (e: InvalidBackupException) {
            BackupDto(
                name = path.name,
                sizeBytes = size,
                createdAt = path.getLastModifiedTime().toInstant(),
                gameyfinVersion = null,
                databaseProduct = null,
                includesMedia = false,
                restorable = false,
                problem = e.message
            )
        }
    }
}
