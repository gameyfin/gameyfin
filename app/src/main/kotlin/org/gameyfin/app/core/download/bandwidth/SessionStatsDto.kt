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
    /**
     * History of bandwidth usage over the last 10 seconds.
     * First element is the most recent, last element is the oldest.
     * Each element represents bytes per second at that point in time.
     */
    val bandwidthHistory: List<Long> = emptyList()
)