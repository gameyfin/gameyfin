package org.gameyfin.app.system.backup

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

enum class BackupTrigger {
    /** Backup created by an admin */
    MANUAL,

    /** Backup of the current database created automatically before a backup is restored */
    PRE_RESTORE
}

enum class BackupTaskStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
