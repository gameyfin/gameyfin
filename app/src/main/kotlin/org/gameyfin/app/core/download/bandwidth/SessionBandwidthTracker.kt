package org.gameyfin.app.core.download.bandwidth

import com.google.common.util.concurrent.RateLimiter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong


/**
 * Tracks bandwidth usage for a single session across all their downloads.
 * Thread-safe for concurrent downloads using Google Guava's RateLimiter.
 */
@Suppress("UnstableApiUsage")
class SessionBandwidthTracker(
    val sessionId: String,
    @Volatile private var maxBytesPerSecond: Long
) {
    // Guava RateLimiter for thread-safe bandwidth throttling
    // Only created when bandwidth limiting is enabled (maxBytesPerSecond > 0)
    private var rateLimiter: RateLimiter? = if (maxBytesPerSecond > 0) {
        RateLimiter.create(maxBytesPerSecond.toDouble())
    } else {
        null
    }

    // Total bytes transferred for the lifetime of this session (for UI display)
    private val totalBytesTransferredAtomic = AtomicLong(0)
    var totalBytesTransferred: Long
        get() = totalBytesTransferredAtomic.get()
        private set(value) {
            totalBytesTransferredAtomic.set(value)
        }

    // For monitoring: bytes written in the current measurement window (lock-free)
    private val bytesWrittenAtomic = AtomicLong(0)

    // For monitoring: start time of the current measurement window
    @Volatile
    private var monitoringWindowStart: Long = System.nanoTime()

    // Timestamp of when the session first started (for UI display only)
    @Volatile
    var startTime: Long = System.nanoTime()
        private set

    @Volatile
    var lastActivityTime: Long = System.nanoTime()
        private set

    val activeDownloads = AtomicInteger(0)

    // Maximum monitoring window duration before resetting statistics (10 seconds)
    private val monitoringWindowNanos = 10_000_000_000L


    @Volatile
    var username: String? = null
        private set

    @Volatile
    var remoteIp: String = "unknown"
        private set

    // Thread-safe set of currently downloading game IDs
    private val activeGameIds = ConcurrentHashMap.newKeySet<Long>()

    // Bandwidth history: last 30 measurements in bytes per second
    // First element is the newest, last element is the oldest
    private val bandwidthHistory = LinkedList<Long>()
    private val maxHistorySize = 30

    /**
     * Update the bandwidth limit (in case configuration changes)
     */
    fun updateLimit(newLimit: Long) {
        maxBytesPerSecond = newLimit
        if (newLimit > 0) {
            // Create or update RateLimiter
            val limiter = rateLimiter
            if (limiter != null) {
                limiter.rate = newLimit.toDouble()
            } else {
                rateLimiter = RateLimiter.create(newLimit.toDouble())
            }
        } else {
            // Unlimited bandwidth - don't need RateLimiter
            rateLimiter = null
        }
    }

    /**
     * Register a new download starting
     */
    fun downloadStarted(gameId: Long? = null, username: String? = null, remoteIp: String? = null) {
        activeDownloads.incrementAndGet()
        lastActivityTime = System.nanoTime()

        if (username != null && username != "anonymousUser") {
            this.username = username
        }

        if (remoteIp != null) {
            this.remoteIp = remoteIp
        }

        if (gameId != null) {
            activeGameIds.add(gameId)
        }
    }

    /**
     * Register a download completing
     */
    fun downloadCompleted(gameId: Long? = null) {
        val remaining = activeDownloads.decrementAndGet()
        lastActivityTime = System.nanoTime()

        if (gameId != null) {
            activeGameIds.remove(gameId)
        }

        // Reset the tracker when all downloads complete to prevent
        // unlimited burst speed when downloads restart after a pause
        if (remaining == 0) {
            reset()
        }
    }

    /**
     * Get a snapshot of currently downloading game IDs
     */
    fun getActiveGameIds(): Set<Long> {
        return activeGameIds.toSet()
    }

    /**
     * Get a snapshot of the bandwidth history.
     * Returns a list where the first element is the newest measurement.
     */
    @Synchronized
    fun getBandwidthHistory(): List<Long> {
        return bandwidthHistory.toList()
    }

    /**
     * Record the current bandwidth measurement to the history.
     * This should be called periodically (e.g., every second) by the monitoring service.
     */
    @Synchronized
    fun recordBandwidthSnapshot() {
        val currentRate = getCurrentBytesPerSecond()

        // Add new measurement at the front
        bandwidthHistory.addLast(currentRate)

        // Remove oldest measurement if we exceed the max size
        if (bandwidthHistory.size > maxHistorySize) {
            bandwidthHistory.removeFirst()
        }
    }

    /**
     * Update monitoring statistics for bytes transferred.
     * This is lock-free for maximum performance during high-bandwidth transfers.
     */
    private fun updateMonitoringStatistics(bytes: Long) {
        val currentTime = System.nanoTime()

        // Check if we need to reset monitoring window (lock-free check, occasional race is acceptable)
        val monitoringElapsed = currentTime - monitoringWindowStart
        if (monitoringElapsed > monitoringWindowNanos) {
            // Use synchronized only for the reset operation (infrequent)
            synchronized(this) {
                // Double-check after acquiring lock
                if (currentTime - monitoringWindowStart > monitoringWindowNanos) {
                    bytesWrittenAtomic.set(0)
                    monitoringWindowStart = currentTime
                }
            }
        }

        // Lock-free atomic operations for high-performance byte counting
        bytesWrittenAtomic.addAndGet(bytes)
        totalBytesTransferredAtomic.addAndGet(bytes)
        lastActivityTime = currentTime
    }

    /**
     * Record bytes written without throttling (used for monitoring-only mode).
     */
    fun recordBytes(bytes: Long) {
        updateMonitoringStatistics(bytes)
    }

    /**
     * Throttle the current thread based on session-wide bandwidth usage.
     * This is called by each download stream, but they all share the same bandwidth quota.
     * Uses Guava's RateLimiter which is thread-safe and implements a token bucket algorithm.
     */
    fun throttle(bytes: Long) {
        updateMonitoringStatistics(bytes)

        // Only throttle if RateLimiter exists (bandwidth limit is set)
        rateLimiter?.acquire(bytes.toInt())
    }

    /**
     * Get current transfer rate in bytes per second based on monitoring window
     */
    fun getCurrentBytesPerSecond(): Long {
        val elapsedNanos = System.nanoTime() - monitoringWindowStart
        val elapsedSeconds = elapsedNanos / 1_000_000_000.0
        return if (elapsedSeconds > 0) {
            (bytesWrittenAtomic.get() / elapsedSeconds).toLong()
        } else {
            0L
        }
    }

    /**
     * Reset the tracker (useful if we want to restart bandwidth calculation)
     * Note: This only resets the monitoring calculation, not the total bytes transferred
     */
    fun reset() {
        bytesWrittenAtomic.set(0)
        monitoringWindowStart = System.nanoTime()
        startTime = System.nanoTime()
        lastActivityTime = System.nanoTime()
        // totalBytesTransferred is intentionally NOT reset - we want to keep this for UI display
    }
}