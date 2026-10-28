package org.gameyfin.app.api.v1.dto

import java.time.Instant

data class TaskRunV1(
    val id: String,
    val taskId: String,
    val status: String,
    val startedAt: Instant,
    val finishedAt: Instant?
)
