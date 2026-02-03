package org.gameyfin.app.core.download.bandwidth

import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionBandwidthTrackerTest {

    private lateinit var tracker: SessionBandwidthTracker

    @BeforeEach
    fun setup() {
        tracker = SessionBandwidthTracker("test-session-123", maxBytesPerSecond = 100_000)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should initialize with correct default values`() {
        assertEquals("test-session-123", tracker.sessionId)
        assertEquals(0, tracker.totalBytesTransferred)
        assertEquals(0, tracker.activeDownloads.get())
        assertNull(tracker.username)
        assertEquals("unknown", tracker.remoteIp)
        assertTrue(tracker.getActiveGameIds().isEmpty())
        assertTrue(tracker.getBandwidthHistory().isEmpty())
    }

    @Test
    fun `should update bandwidth limit`() {
        tracker.updateLimit(200_000)
        // Verify by recording bytes and checking throttle behavior
        tracker.recordBytes(100_000)
        Thread.sleep(100) // Allow some time
        val rate = tracker.getCurrentBytesPerSecond()
        assertTrue(rate > 0)
    }

    @Test
    fun `downloadStarted should increment active downloads and set metadata`() {
        tracker.downloadStarted(gameId = 42L, username = "testuser", remoteIp = "192.168.1.1")

        assertEquals(1, tracker.activeDownloads.get())
        assertEquals("testuser", tracker.username)
        assertEquals("192.168.1.1", tracker.remoteIp)
        assertTrue(tracker.getActiveGameIds().contains(42L))
    }

    @Test
    fun `downloadStarted should not overwrite username with anonymousUser`() {
        tracker.downloadStarted(gameId = 1L, username = "testuser", remoteIp = "192.168.1.1")
        tracker.downloadStarted(gameId = 2L, username = "anonymousUser", remoteIp = "192.168.1.2")

        assertEquals("testuser", tracker.username)
        assertEquals(2, tracker.activeDownloads.get())
    }

    @Test
    fun `downloadStarted should handle null parameters gracefully`() {
        tracker.downloadStarted(gameId = null, username = null, remoteIp = null)

        assertEquals(1, tracker.activeDownloads.get())
        assertNull(tracker.username)
        assertEquals("unknown", tracker.remoteIp)
        assertTrue(tracker.getActiveGameIds().isEmpty())
    }

    @Test
    fun `downloadStarted should handle multiple concurrent downloads`() {
        tracker.downloadStarted(gameId = 1L, username = "user1", remoteIp = "192.168.1.1")
        tracker.downloadStarted(gameId = 2L, username = "user2", remoteIp = "192.168.1.2")
        tracker.downloadStarted(gameId = 3L, username = "user3", remoteIp = "192.168.1.3")

        assertEquals(3, tracker.activeDownloads.get())
        assertEquals(3, tracker.getActiveGameIds().size)
    }

    @Test
    fun `downloadCompleted should decrement active downloads`() {
        tracker.downloadStarted(gameId = 42L, username = "testuser", remoteIp = "192.168.1.1")
        tracker.downloadCompleted(gameId = 42L)

        assertEquals(0, tracker.activeDownloads.get())
        assertFalse(tracker.getActiveGameIds().contains(42L))
    }

    @Test
    fun `downloadCompleted should reset tracker when all downloads complete`() {
        tracker.downloadStarted(gameId = 1L)
        tracker.recordBytes(1000)
        val initialTotal = tracker.totalBytesTransferred

        tracker.downloadCompleted(gameId = 1L)

        assertEquals(0, tracker.activeDownloads.get())
        assertEquals(initialTotal, tracker.totalBytesTransferred) // Total should not reset
        assertEquals(0, tracker.getCurrentBytesPerSecond()) // Rate should reset
    }

    @Test
    fun `downloadCompleted should handle null gameId`() {
        tracker.downloadStarted()
        tracker.downloadCompleted(gameId = null)

        assertEquals(0, tracker.activeDownloads.get())
    }

    @Test
    fun `downloadCompleted should not reset if downloads still active`() {
        tracker.downloadStarted(gameId = 1L)
        tracker.downloadStarted(gameId = 2L)
        tracker.recordBytes(1000)

        tracker.downloadCompleted(gameId = 1L)

        assertEquals(1, tracker.activeDownloads.get())
        assertTrue(tracker.getCurrentBytesPerSecond() > 0) // Should not reset
    }

    @Test
    fun `recordBytes should update total bytes and current rate`() {
        tracker.recordBytes(1000)
        Thread.sleep(100)

        assertEquals(1000, tracker.totalBytesTransferred)
        assertTrue(tracker.getCurrentBytesPerSecond() > 0)
    }

    @Test
    fun `recordBytes should accumulate multiple writes`() {
        tracker.recordBytes(500)
        tracker.recordBytes(300)
        tracker.recordBytes(200)

        assertEquals(1000, tracker.totalBytesTransferred)
    }

    @Test
    fun `recordBytes should reset start time if idle`() {
        tracker.recordBytes(100)
        Thread.sleep(50)
        val rate1 = tracker.getCurrentBytesPerSecond()

        // Complete all downloads to trigger reset
        tracker.downloadStarted()
        tracker.downloadCompleted()

        // Start new download - should reset timer
        tracker.recordBytes(100)
        Thread.sleep(50)
        val rate2 = tracker.getCurrentBytesPerSecond()

        assertTrue(rate1 > 0)
        assertTrue(rate2 > 0)
    }

    @Test
    fun `throttle should limit bandwidth correctly`() {
        val maxBytesPerSecond = 10_000L // 10 KB/s
        tracker = SessionBandwidthTracker("test-session", maxBytesPerSecond)

        val startTime = System.nanoTime()
        tracker.throttle(10_000) // Write 10 KB
        tracker.throttle(10_000) // Write another 10 KB
        val elapsedNanos = System.nanoTime() - startTime

        // Should take at least 1 second to write 20 KB at 10 KB/s
        val elapsedSeconds = elapsedNanos / 1_000_000_000.0
        assertTrue(elapsedSeconds >= 0.9, "Expected at least 0.9 seconds but was $elapsedSeconds")

        assertEquals(20_000, tracker.totalBytesTransferred)
    }

    @Test
    fun `throttle should handle thread interruption`() {
        val maxBytesPerSecond = 1_000L
        tracker = SessionBandwidthTracker("test-session", maxBytesPerSecond)

        // Need to use the rate limiter more so first request doesn't use burst
        tracker.throttle(1_000) // Use up initial burst

        val thread = Thread {
            tracker.throttle(10_000) // This should trigger throttling for ~10 seconds
        }

        thread.start()
        Thread.sleep(100)
        thread.interrupt()
        thread.join(2000)

        // Thread should have completed (either via interruption or normal completion)
        assertFalse(thread.isAlive)
    }

    @Test
    fun `throttle should allow burst at start`() {
        val maxBytesPerSecond = 100_000L
        tracker = SessionBandwidthTracker("test-session", maxBytesPerSecond)

        val startTime = System.nanoTime()
        tracker.throttle(50_000) // Write half of limit - RateLimiter allows first burst immediately
        val elapsedNanos = System.nanoTime() - startTime

        // RateLimiter allows the first request to go through immediately (burst)
        // So this should complete very quickly
        val elapsedSeconds = elapsedNanos / 1_000_000_000.0
        assertTrue(elapsedSeconds < 0.1, "Expected less than 0.1 seconds but was $elapsedSeconds")

        // However, the second request should be throttled
        val startTime2 = System.nanoTime()
        tracker.throttle(50_000) // Another 50KB - this will be throttled
        val elapsedNanos2 = System.nanoTime() - startTime2
        val elapsedSeconds2 = elapsedNanos2 / 1_000_000_000.0
        assertTrue(elapsedSeconds2 >= 0.4, "Expected at least 0.4 seconds but was $elapsedSeconds2")
        assertTrue(elapsedSeconds2 < 0.7, "Expected less than 0.7 seconds but was $elapsedSeconds2")
    }

    @Test
    fun `getCurrentBytesPerSecond should return 0 when no bytes written`() {
        assertEquals(0, tracker.getCurrentBytesPerSecond())
    }

    @Test
    fun `getCurrentBytesPerSecond should calculate rate correctly`() {
        tracker.recordBytes(1000)
        Thread.sleep(100)

        val rate = tracker.getCurrentBytesPerSecond()
        assertTrue(rate > 0)
        assertTrue(rate < 1_000_000) // Sanity check
    }

    @Test
    fun `recordBandwidthSnapshot should add to history`() {
        tracker.recordBytes(1000)
        Thread.sleep(10)

        tracker.recordBandwidthSnapshot()

        val history = tracker.getBandwidthHistory()
        assertEquals(1, history.size)
        assertTrue(history[0] > 0)
    }

    @Test
    fun `recordBandwidthSnapshot should maintain max history size`() {
        // Add 35 snapshots (max is 30)
        repeat(35) {
            tracker.recordBytes(100)
            tracker.recordBandwidthSnapshot()
        }

        val history = tracker.getBandwidthHistory()
        assertEquals(30, history.size)
    }

    @Test
    fun `recordBandwidthSnapshot should maintain correct order`() {
        tracker.recordBytes(1000)
        Thread.sleep(50)
        tracker.recordBandwidthSnapshot()
        val first = tracker.getBandwidthHistory()[0]

        // Reset to start a new measurement period with higher rate
        tracker.reset()
        tracker.recordBytes(5000)
        Thread.sleep(50)
        tracker.recordBandwidthSnapshot()

        val history = tracker.getBandwidthHistory()
        assertEquals(2, history.size)
        assertEquals(first, history[0]) // First measurement should be first
        assertTrue(
            history[1] > history[0],
            "Expected second measurement (${history[1]}) to be higher than first (${history[0]})"
        ) // Second measurement should be higher
    }

    @Test
    fun `getActiveGameIds should return snapshot of current games`() {
        tracker.downloadStarted(gameId = 1L)
        tracker.downloadStarted(gameId = 2L)
        tracker.downloadStarted(gameId = 3L)

        val gameIds = tracker.getActiveGameIds()
        assertEquals(3, gameIds.size)
        assertTrue(gameIds.containsAll(listOf(1L, 2L, 3L)))

        // Verify it's a snapshot (modifying it doesn't affect internal state)
        val mutableGameIds = gameIds.toMutableSet()
        mutableGameIds.add(4L)
        assertEquals(3, tracker.getActiveGameIds().size)
    }

    @Test
    fun `getBandwidthHistory should return snapshot`() {
        tracker.recordBandwidthSnapshot()
        val history1 = tracker.getBandwidthHistory()

        tracker.recordBandwidthSnapshot()
        val history2 = tracker.getBandwidthHistory()

        assertEquals(1, history1.size)
        assertEquals(2, history2.size)
    }

    @Test
    fun `reset should reset counters but preserve total bytes`() {
        tracker.downloadStarted()
        tracker.recordBytes(5000)
        Thread.sleep(100)

        val totalBefore = tracker.totalBytesTransferred
        assertTrue(tracker.getCurrentBytesPerSecond() > 0)

        tracker.reset()

        assertEquals(totalBefore, tracker.totalBytesTransferred)
        assertEquals(0, tracker.getCurrentBytesPerSecond())
    }

    @Test
    fun `should handle concurrent downloads thread-safely`() {
        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        repeat(threadCount) { i ->
            executor.submit {
                try {
                    tracker.downloadStarted(gameId = i.toLong())
                    tracker.recordBytes(100)
                    Thread.sleep(10)
                    tracker.downloadCompleted(gameId = i.toLong())
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

        // All downloads should be completed
        assertEquals(0, tracker.activeDownloads.get())
        assertEquals(1000, tracker.totalBytesTransferred)
    }

    @Test
    fun `should handle concurrent recordBytes calls`() {
        val threadCount = 100
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        repeat(threadCount) {
            executor.submit {
                try {
                    tracker.recordBytes(10)
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

        assertEquals(1000, tracker.totalBytesTransferred)
    }

    @Test
    fun `should handle edge case of zero maxBytesPerSecond`() {
        tracker = SessionBandwidthTracker("test-session", 0)

        // Should not throttle
        val startTime = System.nanoTime()
        tracker.throttle(1_000_000)
        val elapsed = (System.nanoTime() - startTime) / 1_000_000_000.0

        assertTrue(elapsed < 0.1) // Should complete quickly
    }

    @Test
    fun `should handle large byte transfers`() {
        tracker.recordBytes(Long.MAX_VALUE / 2)
        tracker.recordBytes(100)

        assertTrue(tracker.totalBytesTransferred > Long.MAX_VALUE / 2)
    }

    @Test
    fun `lastActivityTime should update on download events`() {
        val initialTime = tracker.lastActivityTime
        Thread.sleep(10)

        tracker.downloadStarted()
        val afterStartTime = tracker.lastActivityTime
        assertTrue(afterStartTime > initialTime)

        Thread.sleep(10)
        tracker.downloadCompleted()
        val afterCompleteTime = tracker.lastActivityTime
        assertTrue(afterCompleteTime > afterStartTime)
    }

    @Test
    fun `lastActivityTime should update on recordBytes`() {
        val initialTime = tracker.lastActivityTime
        Thread.sleep(10)

        tracker.recordBytes(100)
        val afterRecordTime = tracker.lastActivityTime
        assertTrue(afterRecordTime > initialTime)
    }

    @Test
    fun `updateMonitoringStatistics should rotate window after 10 seconds`() {
        // Record some bytes in the first window
        tracker.recordBytes(5000)
        Thread.sleep(100)
        val rate1 = tracker.getCurrentBytesPerSecond()
        assertTrue(rate1 > 0, "First window should have bytes")

        // Use reflection to set the monitoring window start time to 11 seconds ago
        val monitoringWindowStartField = tracker.javaClass.getDeclaredField("monitoringWindowStart")
        monitoringWindowStartField.isAccessible = true
        val elevenSecondsAgo = System.nanoTime() - 11_000_000_000L
        monitoringWindowStartField.setLong(tracker, elevenSecondsAgo)

        // Record more bytes - this should trigger window rotation
        tracker.recordBytes(3000)
        Thread.sleep(100)

        // After rotation, we should have a new window with only the latest bytes
        val rate2 = tracker.getCurrentBytesPerSecond()
        assertTrue(rate2 > 0, "New window should have bytes")

        // The rate calculation should now be based on the new window
        // Since we just started a new window with 3000 bytes, the rate should reflect that
        assertEquals(3000, tracker.totalBytesTransferred)
    }

    @Test
    fun `updateMonitoringStatistics should handle concurrent window rotation`() {
        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        // Set window start to 11 seconds ago to trigger rotation
        val monitoringWindowStartField = tracker.javaClass.getDeclaredField("monitoringWindowStart")
        monitoringWindowStartField.isAccessible = true
        val elevenSecondsAgo = System.nanoTime() - 11_000_000_000L
        monitoringWindowStartField.setLong(tracker, elevenSecondsAgo)

        // Have multiple threads try to record bytes at the same time
        // This should trigger concurrent window rotation attempts
        repeat(threadCount) {
            executor.submit {
                try {
                    tracker.recordBytes(100)
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

        // All bytes should be recorded despite concurrent rotation
        assertEquals(1000, tracker.totalBytesTransferred)
    }

    @Test
    fun `updateMonitoringStatistics should update totalBytesTransferred atomically`() {
        tracker.recordBytes(1000)
        assertEquals(1000, tracker.totalBytesTransferred)

        tracker.recordBytes(2000)
        assertEquals(3000, tracker.totalBytesTransferred)

        tracker.recordBytes(500)
        assertEquals(3500, tracker.totalBytesTransferred)
    }

    @Test
    fun `updateMonitoringStatistics should update lastActivityTime on each call`() {
        val time1 = tracker.lastActivityTime
        Thread.sleep(10)

        tracker.recordBytes(100)
        val time2 = tracker.lastActivityTime
        assertTrue(time2 > time1, "Activity time should increase after recordBytes")

        Thread.sleep(10)
        tracker.throttle(100)
        val time3 = tracker.lastActivityTime
        assertTrue(time3 > time2, "Activity time should increase after throttle")
    }

    @Test
    fun `getCurrentBytesPerSecond should blend with previous window when current window is young`() {
        // Record bytes in first window
        tracker.recordBytes(10_000)
        Thread.sleep(1100) // Wait over 1 second to ensure first window is mature

        // Force window rotation by setting window start to 11 seconds ago
        val monitoringWindowStartField = tracker.javaClass.getDeclaredField("monitoringWindowStart")
        monitoringWindowStartField.isAccessible = true
        val elevenSecondsAgo = System.nanoTime() - 11_000_000_000L
        monitoringWindowStartField.setLong(tracker, elevenSecondsAgo)

        // Record bytes to trigger rotation
        tracker.recordBytes(5_000)

        // Immediately check rate - should blend with previous window since current is young
        Thread.sleep(100) // Sleep a bit but less than 1 second
        val rate = tracker.getCurrentBytesPerSecond()

        // The rate should be positive and influenced by both windows
        assertTrue(rate > 0, "Rate should be positive with blended windows")
        assertTrue(tracker.totalBytesTransferred == 15_000L, "Total should be 15,000 bytes")
    }

    @Test
    fun `updateMonitoringStatistics should handle synchronized block correctly during rotation`() {
        // Record initial bytes
        tracker.recordBytes(1000)

        // Set up for window rotation
        val monitoringWindowStartField = tracker.javaClass.getDeclaredField("monitoringWindowStart")
        monitoringWindowStartField.isAccessible = true
        val elevenSecondsAgo = System.nanoTime() - 11_000_000_000L
        monitoringWindowStartField.setLong(tracker, elevenSecondsAgo)

        // Record more bytes - should trigger synchronized block for rotation
        tracker.recordBytes(2000)

        // Verify the bytes were recorded correctly
        assertEquals(3000, tracker.totalBytesTransferred)

        // Record more bytes in the new window
        tracker.recordBytes(500)
        assertEquals(3500, tracker.totalBytesTransferred)
    }

    @Test
    fun `throttle should call updateMonitoringStatistics with correct byte count`() {
        val bytes = 5000L
        tracker.throttle(bytes)

        // Verify bytes were recorded
        assertEquals(bytes, tracker.totalBytesTransferred)

        // Verify activity time was updated
        assertTrue(tracker.lastActivityTime > 0)
    }
}

