package org.gameyfin.app.core.download.bandwidth

/**
 * DTO for bandwidth statistics
 */
data class SessionStatsDto(
    val sessionId: String,
    val username: String?,
    val remoteIp: String,
    val activeDownloads: Int,
    val activeGameIds: List<Long>,
    val totalBytesTransferred: Long,
    val currentBytesPerSecond: Long,
    val currentMbps: Double
)