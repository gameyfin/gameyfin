package org.gameyfin.app.core.download.bandwidth

import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class BandwidthMaintenanceSchedulerTest {

    private lateinit var sessionBandwidthManager: SessionBandwidthManager
    private lateinit var scheduler: BandwidthMaintenanceScheduler

    @BeforeEach
    fun setup() {
        sessionBandwidthManager = mockk<SessionBandwidthManager>(relaxed = true)
        scheduler = BandwidthMaintenanceScheduler(sessionBandwidthManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `cleanupInactiveTrackers should call cleanup on manager`() {
        every { sessionBandwidthManager.getStats() } returns emptyMap()

        scheduler.cleanupInactiveTrackers()

        verify(exactly = 1) { sessionBandwidthManager.cleanupInactiveTrackers(24.hours) }
    }

    @Test
    fun `cleanupInactiveTrackers should retrieve stats before and after cleanup`() {
        val statsBefore = mapOf(
            "session-1" to mockk<SessionStats>(),
            "session-2" to mockk<SessionStats>(),
            "session-3" to mockk<SessionStats>()
        )
        val statsAfter = mapOf(
            "session-1" to mockk<SessionStats>()
        )

        every { sessionBandwidthManager.getStats() } returnsMany listOf(statsBefore, statsAfter)

        scheduler.cleanupInactiveTrackers()

        verify(exactly = 2) { sessionBandwidthManager.getStats() }
        verify(exactly = 1) { sessionBandwidthManager.cleanupInactiveTrackers(24.hours) }
    }

    @Test
    fun `cleanupInactiveTrackers should handle empty stats`() {
        every { sessionBandwidthManager.getStats() } returns emptyMap()

        assertDoesNotThrow {
            scheduler.cleanupInactiveTrackers()
        }

        verify(exactly = 1) { sessionBandwidthManager.cleanupInactiveTrackers(24.hours) }
    }

    @Test
    fun `cleanupInactiveTrackers should handle exception from manager`() {
        every { sessionBandwidthManager.getStats() } returns emptyMap()
        every { sessionBandwidthManager.cleanupInactiveTrackers(any()) } throws RuntimeException("Test exception")

        assertThrows(RuntimeException::class.java) {
            scheduler.cleanupInactiveTrackers()
        }

        verify(exactly = 1) { sessionBandwidthManager.cleanupInactiveTrackers(24.hours) }
    }

    @Test
    fun `cleanupInactiveTrackers should handle exception when getting stats`() {
        every { sessionBandwidthManager.getStats() } throws RuntimeException("Stats error")

        assertThrows(RuntimeException::class.java) {
            scheduler.cleanupInactiveTrackers()
        }
    }

    @Test
    fun `cleanupInactiveTrackers should use correct threshold`() {
        val thresholdSlot = slot<Duration>()
        every { sessionBandwidthManager.getStats() } returns emptyMap()
        every { sessionBandwidthManager.cleanupInactiveTrackers(capture(thresholdSlot)) } returns Unit

        scheduler.cleanupInactiveTrackers()

        verify(exactly = 1) { sessionBandwidthManager.cleanupInactiveTrackers(any()) }
        assertEquals(24.hours, thresholdSlot.captured)
    }

    @Test
    fun `cleanupInactiveTrackers should be idempotent`() {
        every { sessionBandwidthManager.getStats() } returns emptyMap()

        scheduler.cleanupInactiveTrackers()
        scheduler.cleanupInactiveTrackers()
        scheduler.cleanupInactiveTrackers()

        verify(exactly = 3) { sessionBandwidthManager.cleanupInactiveTrackers(24.hours) }
        verify(exactly = 6) { sessionBandwidthManager.getStats() } // 2 per call
    }

    @Test
    fun `cleanupInactiveTrackers should work with large number of sessions`() {
        val largeStats = (1..1000).associate {
            "session-$it" to mockk<SessionStats>()
        }

        every { sessionBandwidthManager.getStats() } returns largeStats

        assertDoesNotThrow {
            scheduler.cleanupInactiveTrackers()
        }

        verify(exactly = 1) { sessionBandwidthManager.cleanupInactiveTrackers(24.hours) }
    }

    @Test
    fun `should be a Spring Component`() {
        // Verify the annotation is present
        val annotations = BandwidthMaintenanceScheduler::class.annotations
        assertTrue(annotations.any { it.annotationClass.simpleName == "Component" })
    }

    @Test
    fun `cleanupInactiveTrackers should have Scheduled annotation`() {
        // Verify the method has the @Scheduled annotation
        val method = BandwidthMaintenanceScheduler::class.java
            .getDeclaredMethod("cleanupInactiveTrackers")

        val scheduledAnnotation = method.annotations
            .find { it.annotationClass.simpleName == "Scheduled" }

        assertNotNull(scheduledAnnotation)
    }

    @Test
    fun `should handle rapid successive calls`() {
        every { sessionBandwidthManager.getStats() } returns emptyMap()

        repeat(10) {
            scheduler.cleanupInactiveTrackers()
        }

        verify(exactly = 10) { sessionBandwidthManager.cleanupInactiveTrackers(24.hours) }
    }

    @Test
    fun `should handle manager returning different stats each time`() {
        val calls = mutableListOf<Map<String, SessionStats>>()

        // Different stats for each call
        every { sessionBandwidthManager.getStats() } answers {
            val stats = mapOf("session-${calls.size}" to mockk<SessionStats>())
            calls.add(stats)
            stats
        }

        scheduler.cleanupInactiveTrackers()
        scheduler.cleanupInactiveTrackers()

        assertEquals(4, calls.size) // 2 calls per cleanup (before and after)
        verify(exactly = 2) { sessionBandwidthManager.cleanupInactiveTrackers(24.hours) }
    }

    @Test
    fun `should work correctly with real SessionBandwidthManager`() {
        val realManager = SessionBandwidthManager()
        val realScheduler = BandwidthMaintenanceScheduler(realManager)

        // Add some trackers
        val tracker1 = realManager.getTracker("session-1", 100_000)
        val tracker2 = realManager.getTracker("session-2", 200_000)

        tracker1.downloadStarted()
        tracker2.downloadStarted()
        tracker2.downloadCompleted()

        assertEquals(2, realManager.getStats().size)

        // Cleanup with zero threshold should remove inactive trackers
        realScheduler.cleanupInactiveTrackers()

        // With 24 hour threshold, nothing should be removed yet
        assertEquals(2, realManager.getStats().size)
    }
}

