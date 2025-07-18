package org.gameyfin.app.libraries.dto

import org.gameyfin.app.libraries.LibraryScanResult
import org.gameyfin.app.libraries.enums.ScanType
import java.time.Instant
import java.util.*

data class LibraryScanProgress(
    val scanId: UUID = UUID.randomUUID(),
    val libraryId: Long,
    val type: ScanType,
    var status: LibraryScanStatus = LibraryScanStatus.IN_PROGRESS,
    var currentStep: LibraryScanStep,
    val startedAt: Instant = Instant.now(),
    var finishedAt: Instant? = null,
    var result: LibraryScanResult? = null
)

data class LibraryScanStep(
    val description: String,
    var current: Int? = null,
    var total: Int? = null
)

enum class LibraryScanStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}