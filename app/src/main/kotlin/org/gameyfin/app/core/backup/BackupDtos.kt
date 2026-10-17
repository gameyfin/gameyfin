package org.gameyfin.app.core.backup

import java.time.Instant

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

/** Progress reported by the [DatabaseExporter] */
data class BackupStep(
    val description: String,
    val current: Int? = null,
    val total: Int? = null
)
