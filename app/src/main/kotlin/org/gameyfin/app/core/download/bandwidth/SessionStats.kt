package org.gameyfin.app.core.download.bandwidth

import org.gameyfin.app.core.nanoTimeToInstant


/**
 * Statistics for a session's bandwidth usage
 */
data class SessionStats(
    val sessionId: String,
    val startTime: Long,
    val username: String?,
    val remoteIp: String,
    val activeDownloads: Int,
    val activeGameIds: Set<Long>,
    val totalBytesTransferred: Long,
    val currentBytesPerSecond: Long
)

fun SessionStats.toDto(): SessionStatsDto {
    return SessionStatsDto(
        sessionId = this.sessionId,
        startTime = nanoTimeToInstant(this.startTime),
        username = this.username,
        remoteIp = this.remoteIp,
        activeDownloads = this.activeDownloads,
        activeGameIds = this.activeGameIds.toList(),
        totalBytesTransferred = this.totalBytesTransferred,
        currentBytesPerSecond = this.currentBytesPerSecond,
        currentMbps = (this.currentBytesPerSecond / 125_000.0)
    )
}

fun Collection<SessionStats>.toDtos(): List<SessionStatsDto> {
    return this.map { it.toDto() }
}
