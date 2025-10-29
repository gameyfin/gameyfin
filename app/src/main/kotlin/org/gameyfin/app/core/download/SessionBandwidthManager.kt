package org.gameyfin.app.core.download

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.LockSupport

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
                activeDownloads = tracker.activeDownloads.get(),
                totalBytesTransferred = tracker.bytesWritten,
                currentBytesPerSecond = tracker.getCurrentBytesPerSecond()
            )
        }
    }

    /**
     * Clean up inactive trackers (no active downloads for >5 minutes)
     */
    fun cleanupInactiveTrackers() {
        val now = System.nanoTime()
        val inactiveThresholdNanos = 5 * 60 * 1_000_000_000L // 5 minutes

        sessionTrackers.entries.removeIf { (_, tracker) ->
            tracker.activeDownloads.get() == 0 &&
                    (now - tracker.lastActivityTime) > inactiveThresholdNanos
        }
    }
}

/**
 * Tracks bandwidth usage for a single session across all their downloads.
 * Thread-safe for concurrent downloads.
 */
class SessionBandwidthTracker(
    val sessionId: String,
    @Volatile private var maxBytesPerSecond: Long
) {
    @Volatile
    var bytesWritten: Long = 0
        private set

    @Volatile
    var startTime: Long = System.nanoTime()
        private set

    @Volatile
    var lastActivityTime: Long = System.nanoTime()
        private set

    val activeDownloads = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * Update the bandwidth limit (in case configuration changes)
     */
    fun updateLimit(newLimit: Long) {
        maxBytesPerSecond = newLimit
    }

    /**
     * Register a new download starting
     */
    fun downloadStarted() {
        activeDownloads.incrementAndGet()
        lastActivityTime = System.nanoTime()
    }

    /**
     * Register a download completing
     */
    fun downloadCompleted() {
        val remaining = activeDownloads.decrementAndGet()
        lastActivityTime = System.nanoTime()

        // Reset the tracker when all downloads complete to prevent
        // unlimited burst speed when downloads restart after a pause
        if (remaining == 0) {
            reset()
        }
    }

    /**
     * Throttle the current thread based on session-wide bandwidth usage.
     * This is called by each download stream, but they all share the same bandwidth quota.
     */
    @Synchronized
    fun throttle(bytes: Long) {
        // If this is the first write after being idle, reset the timer
        if (bytesWritten == 0L) {
            startTime = System.nanoTime()
        }

        bytesWritten += bytes
        lastActivityTime = System.nanoTime()

        val elapsedNanos = lastActivityTime - startTime
        val elapsedSeconds = elapsedNanos / 1_000_000_000.0

        // Calculate how many bytes we should have written by now
        val expectedBytes = (elapsedSeconds * maxBytesPerSecond).toLong()

        // If we've written more than expected, sleep to catch up
        if (bytesWritten > expectedBytes) {
            val bytesAhead = bytesWritten - expectedBytes
            val sleepTimeNanos = (bytesAhead * 1_000_000_000.0 / maxBytesPerSecond).toLong()

            if (sleepTimeNanos > 0) {
                // Use LockSupport.parkNanos for virtual thread compatibility
                LockSupport.parkNanos(sleepTimeNanos)

                // Check if interrupted
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    /**
     * Get current transfer rate in bytes per second
     */
    fun getCurrentBytesPerSecond(): Long {
        val elapsedNanos = System.nanoTime() - startTime
        val elapsedSeconds = elapsedNanos / 1_000_000_000.0
        return if (elapsedSeconds > 0) {
            (bytesWritten / elapsedSeconds).toLong()
        } else {
            0L
        }
    }

    /**
     * Reset the tracker (useful if we want to restart bandwidth calculation)
     */
    fun reset() {
        bytesWritten = 0
        startTime = System.nanoTime()
        lastActivityTime = System.nanoTime()
    }
}

/**
 * Statistics for a session's bandwidth usage
 */
data class SessionStats(
    val sessionId: String,
    val activeDownloads: Int,
    val totalBytesTransferred: Long,
    val currentBytesPerSecond: Long
)

