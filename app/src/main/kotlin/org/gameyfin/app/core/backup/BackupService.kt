package org.gameyfin.app.core.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import liquibase.integration.spring.SpringResourceAccessor
import org.gameyfin.app.system.SystemService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.info.BuildProperties
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
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
    resourceLoader: ResourceLoader,
    @Value($$"${gameyfin.backup.directory:./backups}") backupDirectory: String,
    @Value($$"${spring.content.fs.filesystem-root:./data/}") mediaDirectory: String,
    @Value($$"${spring.liquibase.change-log:classpath:/db/changelog/db.changelog-master.yaml}") changeLog: String,
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val PRE_RESTORE_SUFFIX = "pre-restore"
        private const val UPLOADED_SUFFIX = "uploaded"
    }

    private val storage = BackupStorage(Path.of(backupDirectory))
    private val mediaRoot: Path = Path.of(mediaDirectory)
    private val exporter = DatabaseExporter()
    private val restorer = DatabaseRestorer(dataSource, changeLog, SpringResourceAccessor(resourceLoader))
    private val progressTracker = BackupProgressTracker()
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

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

    fun getBackups(): List<BackupDto> =
        storage.list().map { toDto(it) }.sortedByDescending { it.createdAt }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    fun subscribeToProgressEvents(): Flux<List<BackupProgress>> = progressTracker.subscribe()

    /**
     * Starts the creation of a backup in the background.
     * The progress is published via [subscribeToProgressEvents].
     *
     * @return the initial progress of the backup
     */
    fun createBackup(includeMedia: Boolean): BackupProgress {
        acquireOperationPermit()
        val progress = progressTracker.start(BackupTrigger.MANUAL, includeMedia)
        try {
            executor.execute {
                try {
                    writeBackup(progress, suffix = null)
                } catch (e: Exception) {
                    log.error(e) { "Could not create backup" }
                } finally {
                    operationPermit.release()
                }
            }
        } catch (e: RejectedExecutionException) {
            progressTracker.fail(progress.backupId, "Gameyfin is shutting down")
            operationPermit.release()
            throw IllegalStateException("Gameyfin is shutting down", e)
        }
        return progress
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
                val safetyBackup = writeBackup(
                    progressTracker.start(BackupTrigger.PRE_RESTORE, includesMedia = false),
                    suffix = PRE_RESTORE_SUFFIX
                )
                log.info { "Created backup ${safetyBackup.name} of the current database before restoring" }
            }

            storage.schedulePendingRestore(name)
            restartPending = true
        }

        log.info { "Restarting Gameyfin to restore backup $name" }
        systemService.restart()
    }

    fun getLastRestoreStatus(): RestoreStatusDto? = storage.readRestoreStatus()

    private fun acquireOperationPermit() {
        if (!operationPermit.tryAcquire()) {
            throw IllegalStateException("Another backup operation is currently in progress")
        }
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

    private fun writeBackup(progress: BackupProgress, suffix: String?): Path {
        val includeMedia = progress.includesMedia
        val createdAt = Instant.now()
        log.info { "Creating backup (media files ${if (includeMedia) "included" else "excluded"})" }

        val tempFile = try {
            storage.createTempFile()
        } catch (e: Exception) {
            progressTracker.fail(progress.backupId, e.message)
            throw e
        }

        try {
            Files.newOutputStream(tempFile).use { output ->
                exporter.writeArchive(
                    dataSource = dataSource,
                    output = output,
                    createdAt = createdAt,
                    appKeyFingerprint = AppKeyFingerprint.current(),
                    gameyfinVersion = buildProperties.ifAvailable?.version,
                    mediaRoot = if (includeMedia) mediaRoot else null,
                    progress = { step -> progressTracker.update(progress.backupId, step) }
                )
            }

            val path = storage.publish(tempFile, storage.newBackupName(createdAt, suffix))
            log.info { "Created backup ${path.name}" }
            progressTracker.complete(progress.backupId, path.name, path.fileSize())
            return path
        } catch (e: Exception) {
            tempFile.deleteIfExists()
            progressTracker.fail(progress.backupId, e.message)
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
