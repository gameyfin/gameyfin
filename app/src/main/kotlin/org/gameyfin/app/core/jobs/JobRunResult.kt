package org.gameyfin.app.core.jobs

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
data class JobRunResult(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val jobName: String,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime?,
    @Enumerated(EnumType.STRING)
    val status: JobRunStatus,
    val message: String?
)
