package org.gameyfin.app.core.backup

import java.time.Instant
import java.util.*

data class BackupDto(
    val name: String,
    val sizeBytes: Long,
    val createdAt: Instant,
    val gameyfinVersion: String?,
    val databaseProduct: String?,
    val includesMedia: Boolean,
    /** Whether this backup can be restored by this instance; if not, [problem] contains the reason */
    val restorable: Boolean,
    val problem: String?
)

data class RestoreStatusDto(
    val backupName: String,
    val success: Boolean,
    val message: String,
    val finishedAt: Instant
)

/**
 * Progress of a backup creation, published to admins via [BackupEndpoint.subscribeToBackupProgressEvents].
 */
data class BackupProgress(
    val backupId: UUID = UUID.randomUUID(),
    val trigger: BackupTrigger,
    val includesMedia: Boolean,
    val status: BackupTaskStatus = BackupTaskStatus.IN_PROGRESS,
    val currentStep: BackupStep,
    val startedAt: Instant = Instant.now(),
    val finishedAt: Instant? = null,
    /** Name of the created backup, set once the backup has been completed */
    val backupName: String? = null,
    val sizeBytes: Long? = null,
    val errorMessage: String? = null
)

data class BackupStep(
    val description: String,
    val current: Int? = null,
    val total: Int? = null
)

/**
 * Progress of the recovery of images which are missing after a backup has been restored,
 * published to admins via [BackupEndpoint.subscribeToImageRecoveryEvents].
 */
data class ImageRecoveryProgress(
    val taskId: UUID = UUID.randomUUID(),
    val status: BackupTaskStatus = BackupTaskStatus.IN_PROGRESS,
    val currentStep: BackupStep,
    val startedAt: Instant = Instant.now(),
    val finishedAt: Instant? = null,
    /** Number of images which could be downloaded again from their original URL */
    val restored: Int = 0,
    /** Number of images which are no longer available and have been removed (including all references) */
    val removed: Int = 0,
    /** Number of images which could not be downloaded due to a (possibly temporary) error and have been kept */
    val failed: Int = 0,
    val errorMessage: String? = null
)

enum class BackupTrigger {
    /** Backup created by an admin */
    MANUAL,

    /** Backup of the current database created automatically before a backup is restored */
    PRE_RESTORE,

    /** Backup created automatically to transfer the data to a different database */
    DATABASE_SWITCH
}

enum class BackupTaskStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
