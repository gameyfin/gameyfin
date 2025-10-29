package org.gameyfin.app.core.download

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role

/**
 * Endpoint for monitoring active download bandwidth usage.
 * Only accessible by administrators.
 */
@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class BandwidthMonitoringEndpoint(
    private val sessionBandwidthManager: SessionBandwidthManager
) {

    /**
     * Get bandwidth statistics for all active sessions
     */
    fun getActiveSessions(): List<SessionStatsDto> {
        return sessionBandwidthManager.getStats().values.map { stats ->
            SessionStatsDto(
                sessionId = stats.sessionId,
                activeDownloads = stats.activeDownloads,
                totalBytesTransferred = stats.totalBytesTransferred,
                currentBytesPerSecond = stats.currentBytesPerSecond,
                currentMbps = (stats.currentBytesPerSecond / 125_000.0)
            )
        }.sortedByDescending { it.currentBytesPerSecond }
    }

    /**
     * Clear a specific session's bandwidth tracker
     * (useful for testing or if a session is stuck)
     */
    fun clearSession(sessionId: String) {
        sessionBandwidthManager.removeTracker(sessionId)
    }
}

/**
 * DTO for bandwidth statistics
 */
data class SessionStatsDto(
    val sessionId: String,
    val activeDownloads: Int,
    val totalBytesTransferred: Long,
    val currentBytesPerSecond: Long,
    val currentMbps: Double
)

