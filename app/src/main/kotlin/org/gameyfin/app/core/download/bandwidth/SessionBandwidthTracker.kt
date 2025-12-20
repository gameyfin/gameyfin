package org.gameyfin.app.core.download.bandwidth

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
    private val totalBytesTransferredAtomic = AtomicLong(0)
    var totalBytesTransferred: Long
        get() = totalBytesTransferredAtomic.get()
        private set(value) {
            totalBytesTransferredAtomic.set(value)
        }

    // Token bucket for bandwidth limiting - tracks available "tokens" (bytes)
    @Volatile
    private var availableTokens: Double = 0.0

    // Last time tokens were refilled
    @Volatile
    private var lastRefillTime: Long = System.nanoTime()

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

    // Maximum burst size: allow accumulating up to 2 seconds worth of tokens
    private val maxBurstMultiplier = 2.0

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
     * Record bytes written without throttling (used for monitoring-only mode).
     * This is lock-free for maximum performance during high-bandwidth transfers.
     */
    fun recordBytes(bytes: Long) {
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
     * Throttle the current thread based on session-wide bandwidth usage using token bucket algorithm.
     * This is called by each download stream, but they all share the same bandwidth quota.
     */
    @Synchronized
    fun throttle(bytes: Long) {
        val currentTime = System.nanoTime()

        // Update monitoring statistics using atomic operations
        val monitoringElapsed = currentTime - monitoringWindowStart
        if (monitoringElapsed > monitoringWindowNanos) {
            bytesWrittenAtomic.set(0)
            monitoringWindowStart = currentTime
        }
        bytesWrittenAtomic.addAndGet(bytes)
        totalBytesTransferredAtomic.addAndGet(bytes)

        // Skip throttling if no limit is set (0 or negative means unlimited)
        if (maxBytesPerSecond <= 0) {
            lastActivityTime = currentTime
            return
        }

        // Token bucket algorithm: refill tokens based on elapsed time
        val elapsedNanos = currentTime - lastRefillTime
        val elapsedSeconds = elapsedNanos / 1_000_000_000.0

        // Add tokens based on elapsed time
        val tokensToAdd = elapsedSeconds * maxBytesPerSecond
        availableTokens = minOf(
            availableTokens + tokensToAdd,
            maxBytesPerSecond * maxBurstMultiplier // Cap at max burst size
        )
        lastRefillTime = currentTime

        // Try to consume tokens for this write
        if (availableTokens >= bytes) {
            // We have enough tokens, consume them and proceed
            availableTokens -= bytes
            lastActivityTime = currentTime
        } else {
            // Not enough tokens - need to wait
            val tokensNeeded = bytes - availableTokens
            val sleepTimeNanos = (tokensNeeded * 1_000_000_000.0 / maxBytesPerSecond).toLong()

            if (sleepTimeNanos > 0) {
                // Use LockSupport.parkNanos for virtual thread compatibility
                LockSupport.parkNanos(sleepTimeNanos)

                // Check if interrupted
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt()
                }
            }

            // After sleeping, refill tokens and consume
            val currentTimeAfterSleep = System.nanoTime()
            val additionalElapsedNanos = currentTimeAfterSleep - lastRefillTime
            val additionalElapsedSeconds = additionalElapsedNanos / 1_000_000_000.0
            val additionalTokens = additionalElapsedSeconds * maxBytesPerSecond

            availableTokens = minOf(
                availableTokens + additionalTokens,
                maxBytesPerSecond * maxBurstMultiplier
            )
            availableTokens -= bytes
            lastRefillTime = currentTimeAfterSleep
            lastActivityTime = currentTimeAfterSleep
        }
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
     * Note: This only resets the throttling calculation, not the total bytes transferred
     */
    fun reset() {
        availableTokens = 0.0
        lastRefillTime = System.nanoTime()
        bytesWrittenAtomic.set(0)
        monitoringWindowStart = System.nanoTime()
        startTime = System.nanoTime()
        lastActivityTime = System.nanoTime()
        // totalBytesTransferred is intentionally NOT reset - we want to keep this for UI display
    }
}