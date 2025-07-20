package org.gameyfin.app.core.jobs

import org.gameyfin.app.libraries.LibraryScanService
import org.gameyfin.app.libraries.enums.ScanType
import java.time.LocalDateTime

class LibraryScanJob(private val libraryScanService: LibraryScanService) : Job {
    override val name: String = "LibraryScan"
    override fun run(): JobRunResult {
        val startedAt = LocalDateTime.now()
        var finishedAt: LocalDateTime?
        var status: JobRunStatus
        var message: String?
        try {
            libraryScanService.triggerScan(ScanType.SCHEDULED, null)
            message = "Library scan completed successfully"
            status = JobRunStatus.SUCCESS
        } catch (ex: Exception) {
            message = ex.message
            status = JobRunStatus.FAILED
        } finally {
            finishedAt = LocalDateTime.now()
        }
        return JobRunResult(
            jobName = name,
            startedAt = startedAt,
            finishedAt = finishedAt,
            status = status,
            message = message
        )
    }
}

