package de.grimsi.gameyfin.libraries.dto

import de.grimsi.gameyfin.libraries.LibraryScanResult
import java.time.Instant
import java.util.*

data class LibraryScanProgress(
    val scanId: UUID = UUID.randomUUID(),
    val libraryId: Long,
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