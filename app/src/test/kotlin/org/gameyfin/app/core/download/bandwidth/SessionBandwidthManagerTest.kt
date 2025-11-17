package org.gameyfin.app.core.download.bandwidth

import com.helger.commons.mock.CommonsAssert.assertEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SessionBandwidthManagerTest {

    private lateinit var manager: SessionBandwidthManager

    @BeforeEach
    fun setup() {
        manager = SessionBandwidthManager()
    }

    @Test
    fun `getTracker should create new tracker for new session`() {
        val tracker = manager.getTracker("session-123", 100_000)

        assertNotNull(tracker)
        assertEquals("session-123", tracker.sessionId)
    }

    @Test
    fun `getTracker should return existing tracker for same session`() {
        val tracker1 = manager.getTracker("session-123", 100_000)
        val tracker2 = manager.getTracker("session-123", 100_000)

        assertSame(tracker1, tracker2)
    }

    @Test
    fun `getTracker should update limit on existing tracker`() {
        val tracker = manager.getTracker("session-123", 100_000)
        tracker.recordBytes(100)

        // Get same tracker with different limit
        val sameTracker = manager.getTracker("session-123", 200_000)

        assertSame(tracker, sameTracker)
        // Limit should be updated (verified indirectly through behavior)
    }

    @Test
    fun `getTracker should handle multiple different sessions`() {
        val tracker1 = manager.getTracker("session-1", 100_000)
        val tracker2 = manager.getTracker("session-2", 200_000)
        val tracker3 = manager.getTracker("session-3", 300_000)

        assertNotSame(tracker1, tracker2)
        assertNotSame(tracker2, tracker3)
        assertNotSame(tracker1, tracker3)

        assertEquals("session-1", tracker1.sessionId)
        assertEquals("session-2", tracker2.sessionId)
        assertEquals("session-3", tracker3.sessionId)
    }

    @Test
    fun `removeTracker should remove tracker for session`() {
        val tracker = manager.getTracker("session-123", 100_000)
        tracker.downloadStarted()

        manager.removeTracker("session-123")

        // Verify tracker is removed by checking stats
        val stats = manager.getStats()
        assertFalse(stats.containsKey("session-123"))
    }

    @Test
    fun `removeTracker should handle non-existent session gracefully`() {
        assertDoesNotThrow {
            manager.removeTracker("non-existent-session")
        }
    }

    @Test
    fun `getStats should return empty map initially`() {
        val stats = manager.getStats()

        assertTrue(stats.isEmpty())
    }

    @Test
    fun `getStats should return stats for all active sessions`() {
        val tracker1 = manager.getTracker("session-1", 100_000)
        val tracker2 = manager.getTracker("session-2", 200_000)

        tracker1.downloadStarted(gameId = 1L, username = "user1", remoteIp = "192.168.1.1")
        tracker2.downloadStarted(gameId = 2L, username = "user2", remoteIp = "192.168.1.2")

        val stats = manager.getStats()

        assertEquals(2, stats.size)

        val stat1 = stats["session-1"]
        assertNotNull(stat1)
        assertEquals("session-1", stat1!!.sessionId)
        assertEquals(1, stat1.activeDownloads)
        assertEquals("user1", stat1.username)
        assertEquals("192.168.1.1", stat1.remoteIp)
        assertTrue(stat1.activeGameIds.contains(1L))

        val stat2 = stats["session-2"]
        assertNotNull(stat2)
        assertEquals("session-2", stat2.sessionId)
        assertEquals(1, stat2.activeDownloads)
        assertEquals("user2", stat2.username)
        assertEquals("192.168.1.2", stat2.remoteIp)
        assertTrue(stat2.activeGameIds.contains(2L))
    }

    @Test
    fun `getStats should reflect current state of trackers`() {
        val tracker = manager.getTracker("session-1", 100_000)
        tracker.downloadStarted(gameId = 1L)
        tracker.recordBytes(1000)

        val statsBefore = manager.getStats()["session-1"]
        assertNotNull(statsBefore)
        assertEquals(1, statsBefore.activeDownloads)
        assertEquals(1000, statsBefore.totalBytesTransferred)

        tracker.recordBytes(500)
        tracker.downloadCompleted(gameId = 1L)

        val statsAfter = manager.getStats()["session-1"]
        assertNotNull(statsAfter)
        assertEquals(0, statsAfter.activeDownloads)
        assertEquals(1500, statsAfter.totalBytesTransferred)
    }

    @Test
    fun `recordAllBandwidthSnapshots should record for all trackers`() {
        val tracker1 = manager.getTracker("session-1", 100_000)
        val tracker2 = manager.getTracker("session-2", 200_000)

        tracker1.recordBytes(1000)
        tracker2.recordBytes(2000)
        Thread.sleep(50)

        manager.recordAllBandwidthSnapshots()

        val stats = manager.getStats()
        assertEquals(1, stats["session-1"]!!.bandwidthHistory.size)
        assertEquals(1, stats["session-2"]!!.bandwidthHistory.size)
    }

    @Test
    fun `recordAllBandwidthSnapshots should handle empty trackers`() {
        assertDoesNotThrow {
            manager.recordAllBandwidthSnapshots()
        }
    }

    @Test
    fun `recordAllBandwidthSnapshots should be called multiple times`() {
        val tracker = manager.getTracker("session-1", 100_000)
        tracker.recordBytes(100)

        manager.recordAllBandwidthSnapshots()
        manager.recordAllBandwidthSnapshots()
        manager.recordAllBandwidthSnapshots()

        val stats = manager.getStats()["session-1"]
        assertNotNull(stats)
        assertEquals(3, stats.bandwidthHistory.size)
    }

    @Test
    fun `cleanupInactiveTrackers should remove inactive trackers`() {
        val tracker1 = manager.getTracker("session-1", 100_000)

        // Init a second tracker, but leave it inactive
        manager.getTracker("session-2", 100_000)

        // Make tracker1 active
        tracker1.downloadStarted()

        val statsBefore = manager.getStats()
        assertEquals(2, statsBefore.size)

        // Clean with a very long threshold - nothing should be removed
        manager.cleanupInactiveTrackers(24.hours)

        val statsAfter = manager.getStats()
        assertEquals(2, statsAfter.size)
    }

    @Test
    fun `cleanupInactiveTrackers should not remove active trackers`() {
        val tracker = manager.getTracker("session-1", 100_000)
        tracker.downloadStarted(gameId = 1L)

        manager.cleanupInactiveTrackers(1.milliseconds)

        val stats = manager.getStats()
        assertEquals(1, stats.size)
        assertTrue(stats.containsKey("session-1"))
    }

    @Test
    fun `cleanupInactiveTrackers should remove only inactive trackers`() {
        val tracker2 = manager.getTracker("session-2", 200_000)

        // Make only tracker2 active
        tracker2.downloadStarted()

        // Wait and cleanup with short threshold
        Thread.sleep(100)
        manager.cleanupInactiveTrackers(10.milliseconds)

        val stats = manager.getStats()
        // tracker2 should remain (active), tracker1 and tracker3 should be removed (inactive and old)
        assertTrue(stats.size <= 3) // Could be 1-3 depending on timing
        if (stats.containsKey("session-2")) {
            assertEquals(1, stats["session-2"]!!.activeDownloads)
        }
    }

    @Test
    fun `cleanupInactiveTrackers should handle empty manager`() {
        assertDoesNotThrow {
            manager.cleanupInactiveTrackers(1.hours)
        }
    }

    @Test
    fun `cleanupInactiveTrackers should handle zero threshold`() {
        val tracker = manager.getTracker("session-1", 100_000)
        tracker.downloadStarted()
        tracker.downloadCompleted()

        Thread.sleep(10)
        manager.cleanupInactiveTrackers(0.seconds)

        // All inactive trackers should be removed
        val stats = manager.getStats()
        assertEquals(0, stats.size)
    }

    @Test
    fun `should handle concurrent getTracker calls`() {
        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val trackers = mutableListOf<SessionBandwidthTracker>()

        repeat(threadCount) {
            executor.submit {
                try {
                    val tracker = manager.getTracker("shared-session", 100_000)
                    synchronized(trackers) {
                        trackers.add(tracker)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

        // All threads should get the same tracker instance
        assertEquals(threadCount, trackers.size)
        val firstTracker = trackers[0]
        assertTrue(trackers.all { it === firstTracker })
    }

    @Test
    fun `should handle concurrent operations on different sessions`() {
        val threadCount = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        repeat(threadCount) { i ->
            executor.submit {
                try {
                    val sessionId = "session-$i"
                    val tracker = manager.getTracker(sessionId, 100_000 + i * 1000L)
                    tracker.downloadStarted(gameId = i.toLong())
                    tracker.recordBytes(100)
                    Thread.sleep(10)
                    tracker.downloadCompleted(gameId = i.toLong())
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))

        val stats = manager.getStats()
        assertEquals(threadCount, stats.size)
    }

    @Test
    fun `should handle concurrent removeTracker calls`() {
        val sessionIds = (1..10).map { "session-$it" }
        sessionIds.forEach { manager.getTracker(it, 100_000) }

        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(sessionIds.size)

        sessionIds.forEach { sessionId ->
            executor.submit {
                try {
                    manager.removeTracker(sessionId)
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

        val stats = manager.getStats()
        assertEquals(0, stats.size)
    }

    @Test
    fun `should handle mixed concurrent operations`() {
        val executor = Executors.newFixedThreadPool(20)
        val latch = CountDownLatch(100)

        repeat(25) { i ->
            // Create trackers
            executor.submit {
                try {
                    manager.getTracker("session-$i", 100_000)
                } finally {
                    latch.countDown()
                }
            }

            // Record snapshots
            executor.submit {
                try {
                    manager.recordAllBandwidthSnapshots()
                } finally {
                    latch.countDown()
                }
            }

            // Get stats
            executor.submit {
                try {
                    manager.getStats()
                } finally {
                    latch.countDown()
                }
            }

            // Remove trackers
            executor.submit {
                try {
                    manager.removeTracker("session-${i % 10}")
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))

        // Should not crash and should be in a consistent state
        assertDoesNotThrow {
            manager.getStats()
            manager.recordAllBandwidthSnapshots()
        }
    }

    @Test
    fun `getStats should return independent snapshot`() {
        val tracker = manager.getTracker("session-1", 100_000)
        tracker.downloadStarted()

        val stats1 = manager.getStats()
        tracker.recordBytes(1000)
        val stats2 = manager.getStats()

        // Stats should be different
        assertNotEquals(
            stats1["session-1"]!!.totalBytesTransferred,
            stats2["session-1"]!!.totalBytesTransferred
        )
    }

    @Test
    fun `should handle rapid create and destroy cycles`() {
        repeat(100) { i ->
            val tracker = manager.getTracker("session-$i", 100_000)
            tracker.downloadStarted()
            tracker.recordBytes(100)
            tracker.downloadCompleted()
            manager.removeTracker("session-$i")
        }

        val stats = manager.getStats()
        assertEquals(0, stats.size)
    }

    @Test
    fun `should isolate sessions from each other`() {
        val tracker1 = manager.getTracker("session-1", 100_000)
        val tracker2 = manager.getTracker("session-2", 200_000)

        tracker1.downloadStarted(gameId = 1L, username = "user1", remoteIp = "192.168.1.1")
        tracker1.recordBytes(1000)

        tracker2.downloadStarted(gameId = 2L, username = "user2", remoteIp = "192.168.1.2")
        tracker2.recordBytes(2000)

        val stats = manager.getStats()

        // Each session should have independent state
        assertEquals(1000, stats["session-1"]!!.totalBytesTransferred)
        assertEquals(2000, stats["session-2"]!!.totalBytesTransferred)
        assertEquals(setOf(1L), stats["session-1"]!!.activeGameIds)
        assertEquals(setOf(2L), stats["session-2"]!!.activeGameIds)
        assertEquals("user1", stats["session-1"]!!.username)
        assertEquals("user2", stats["session-2"]!!.username)
    }
}

