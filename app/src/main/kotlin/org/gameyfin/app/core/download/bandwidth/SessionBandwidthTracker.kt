package org.gameyfin.app.core.download.bandwidth

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport


/**
 * Tracks bandwidth usage for a single session across all their downloads.
 * Thread-safe for concurrent downloads.
 */
class SessionBandwidthTracker(
    val sessionId: String,
    @Volatile private var maxBytesPerSecond: Long
) {
    // Total bytes transferred for the lifetime of this session (for UI display)
    @Volatile
    var totalBytesTransferred: Long = 0
        private set

    // Bytes used for throttling calculation (resets when all downloads complete)
    @Volatile
    private var bytesWritten: Long = 0

    @Volatile
    var startTime: Long = System.nanoTime()
        private set

    @Volatile
    var lastActivityTime: Long = System.nanoTime()
        private set

    val activeDownloads = AtomicInteger(0)

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
     * Record bytes written without throttling (used for monitoring-only mode)
     */
    @Synchronized
    fun recordBytes(bytes: Long) {
        // If this is the first write after being idle, reset the timer
        if (bytesWritten == 0L) {
            startTime = System.nanoTime()
        }

        bytesWritten += bytes
        totalBytesTransferred += bytes
        lastActivityTime = System.nanoTime()
    }

    /**
     * Throttle the current thread based on session-wide bandwidth usage.
     * This is called by each download stream, but they all share the same bandwidth quota.
     */
    @Synchronized
    fun throttle(bytes: Long) {
        // Skip throttling if no limit is set (0 or negative means unlimited)
        if (maxBytesPerSecond <= 0) {
            // If this is the first write after being idle, reset the timer
            if (bytesWritten == 0L) {
                startTime = System.nanoTime()
            }
            bytesWritten += bytes
            totalBytesTransferred += bytes
            lastActivityTime = System.nanoTime()
            return
        }

        // If this is the first write after being idle, reset the timer
        if (bytesWritten == 0L) {
            startTime = System.nanoTime()
        }

        bytesWritten += bytes
        totalBytesTransferred += bytes

        // Calculate elapsed time BEFORE updating lastActivityTime
        val currentTime = System.nanoTime()
        val elapsedNanos = currentTime - startTime
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

        // Update last activity time after throttling
        lastActivityTime = System.nanoTime()
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
     * Note: This only resets the throttling calculation, not the total bytes transferred
     */
    fun reset() {
        bytesWritten = 0
        startTime = System.nanoTime()
        lastActivityTime = System.nanoTime()
        // totalBytesTransferred is intentionally NOT reset - we want to keep this for UI display
    }
}