package org.gameyfin.app.core.download.bandwidth

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * Manages bandwidth limiting across all downloads for a specific session.
 * This ensures that multiple concurrent downloads by the same user share the bandwidth limit.
 */
@Component
class SessionBandwidthManager {

    // Maps session ID to their bandwidth tracker
    private val sessionTrackers = ConcurrentHashMap<String, SessionBandwidthTracker>()

    /**
     * Get or create a bandwidth tracker for the given session.
     */
    fun getTracker(sessionId: String, maxBytesPerSecond: Long): SessionBandwidthTracker {
        return sessionTrackers.computeIfAbsent(sessionId) {
            SessionBandwidthTracker(sessionId, maxBytesPerSecond)
        }.also {
            // Update the limit in case configuration changed
            it.updateLimit(maxBytesPerSecond)
        }
    }

    /**
     * Remove tracker for a session (call this when session expires or user logs out)
     */
    fun removeTracker(sessionId: String) {
        sessionTrackers.remove(sessionId)
    }

    /**
     * Get current statistics for monitoring
     */
    fun getStats(): Map<String, SessionStats> {
        return sessionTrackers.mapValues { (_, tracker) ->
            SessionStats(
                sessionId = tracker.sessionId,
                startTime = tracker.startTime,
                username = tracker.username,
                remoteIp = tracker.remoteIp,
                activeDownloads = tracker.activeDownloads.get(),
                activeGameIds = tracker.getActiveGameIds(),
                totalBytesTransferred = tracker.totalBytesTransferred,
                currentBytesPerSecond = tracker.getCurrentBytesPerSecond(),
                bandwidthHistory = tracker.getBandwidthHistory()
            )
        }
    }

    /**
     * Record bandwidth snapshots for all active trackers.
     * This should be called periodically (e.g., every second) before collecting stats.
     */
    fun recordAllBandwidthSnapshots() {
        sessionTrackers.values.forEach { tracker ->
            tracker.recordBandwidthSnapshot()
        }
    }

    /**
     * Clean up inactive trackers that have had no active downloads for the specified threshold duration
     */
    fun cleanupInactiveTrackers(inactiveThreshold: Duration) {
        val now = System.nanoTime()
        val inactiveThresholdNanos = inactiveThreshold.inWholeNanoseconds

        sessionTrackers.entries.removeIf { (_, tracker) ->
            tracker.activeDownloads.get() == 0 &&
                    (now - tracker.lastActivityTime) > inactiveThresholdNanos
        }
    }
}

