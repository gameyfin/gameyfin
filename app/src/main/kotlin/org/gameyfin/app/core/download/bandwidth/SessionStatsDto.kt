package org.gameyfin.app.core.download.bandwidth

import java.time.Instant

/**
 * DTO for bandwidth statistics
 */
data class SessionStatsDto(
    val sessionId: String,
    val startTime: Instant,
    val username: String?,
    val remoteIp: String,
    val activeDownloads: Int,
    val activeGameIds: List<Long>,
    val totalBytesTransferred: Long,
    val currentBytesPerSecond: Long,
    val currentMbps: Double
)