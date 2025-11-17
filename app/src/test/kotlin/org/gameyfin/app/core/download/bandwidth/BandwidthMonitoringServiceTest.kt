package org.gameyfin.app.core.download.bandwidth

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Duration
import kotlin.test.*

class BandwidthMonitoringServiceTest {

    private lateinit var sessionBandwidthManager: SessionBandwidthManager
    private lateinit var service: BandwidthMonitoringService

    @BeforeEach
    fun setup() {
        BandwidthMonitoringService.resetSink() // Reset sink for test isolation
        sessionBandwidthManager = mockk<SessionBandwidthManager>(relaxed = true)
        service = BandwidthMonitoringService(sessionBandwidthManager)
    }

    @AfterEach
    fun tearDown() {
        BandwidthMonitoringService.resetSink() // Clean up after test
        unmockkAll()
    }

    @Test
    fun `calculateAndEmitBandwidthUpdates should record snapshots for all sessions`() {
        every { sessionBandwidthManager.getStats() } returns emptyMap()

        service.calculateAndEmitBandwidthUpdates()

        verify(exactly = 1) { sessionBandwidthManager.recordAllBandwidthSnapshots() }
        verify(exactly = 1) { sessionBandwidthManager.getStats() }
    }

    @Test
    fun `calculateAndEmitBandwidthUpdates should emit stats when changed`() {
        val stats = mapOf(
            "session-1" to SessionStats(
                sessionId = "session-1",
                startTime = System.nanoTime(),
                username = "user1",
                remoteIp = "192.168.1.1",
                activeDownloads = 1,
                activeGameIds = setOf(1L),
                totalBytesTransferred = 1000,
                currentBytesPerSecond = 100
            )
        )

        every { sessionBandwidthManager.getStats() } returns stats

        val flux = BandwidthMonitoringService.subscribe()
        val subscriber = StepVerifier.create(flux)

        service.calculateAndEmitBandwidthUpdates()

        subscriber
            .expectNextMatches { it.size == 1 && it[0].size == 1 }
            .thenCancel()
            .verify(Duration.ofSeconds(5))
    }

    @Test
    fun `calculateAndEmitBandwidthUpdates should not emit if stats unchanged`() {
        val stats = mapOf(
            "session-1" to SessionStats(
                sessionId = "session-1",
                startTime = System.nanoTime(),
                username = "user1",
                remoteIp = "192.168.1.1",
                activeDownloads = 1,
                activeGameIds = setOf(1L),
                totalBytesTransferred = 1000,
                currentBytesPerSecond = 100
            )
        )

        every { sessionBandwidthManager.getStats() } returns stats

        // First call should emit
        service.calculateAndEmitBandwidthUpdates()

        // Second call with same stats should not emit
        service.calculateAndEmitBandwidthUpdates()

        verify(exactly = 2) { sessionBandwidthManager.recordAllBandwidthSnapshots() }
        verify(exactly = 2) { sessionBandwidthManager.getStats() }
    }

    @Test
    fun `calculateAndEmitBandwidthUpdates should emit empty stats when all cleared`() {
        // First emit with data
        val stats = mapOf(
            "session-1" to SessionStats(
                sessionId = "session-1",
                startTime = System.nanoTime(),
                username = "user1",
                remoteIp = "192.168.1.1",
                activeDownloads = 1,
                activeGameIds = setOf(1L),
                totalBytesTransferred = 1000,
                currentBytesPerSecond = 100
            )
        )

        every { sessionBandwidthManager.getStats() } returns stats
        service.calculateAndEmitBandwidthUpdates()

        // Then emit empty stats
        every { sessionBandwidthManager.getStats() } returns emptyMap()

        val flux = BandwidthMonitoringService.subscribe()
        val subscriber = StepVerifier.create(flux)
            .expectNextMatches { it.size == 2 && it[0].isNotEmpty() && it[1].isEmpty() }
            .thenCancel()
            .verifyLater()

        service.calculateAndEmitBandwidthUpdates()

        subscriber.verify(Duration.ofSeconds(5))
    }

    @Test
    fun `getActiveSessions should return DTOs of all active sessions`() {
        val stats = mapOf(
            "session-1" to SessionStats(
                sessionId = "session-1",
                startTime = System.nanoTime(),
                username = "user1",
                remoteIp = "192.168.1.1",
                activeDownloads = 1,
                activeGameIds = setOf(1L),
                totalBytesTransferred = 1000,
                currentBytesPerSecond = 100,
                bandwidthHistory = listOf(100L, 90L, 110L)
            ),
            "session-2" to SessionStats(
                sessionId = "session-2",
                startTime = System.nanoTime(),
                username = "user2",
                remoteIp = "192.168.1.2",
                activeDownloads = 2,
                activeGameIds = setOf(2L, 3L),
                totalBytesTransferred = 2000,
                currentBytesPerSecond = 200,
                bandwidthHistory = listOf(200L, 190L, 210L)
            )
        )

        every { sessionBandwidthManager.getStats() } returns stats

        val result = service.getActiveSessions()

        assertEquals(2, result.size)

        val dto1 = result.find { it.sessionId == "session-1" }
        assertNotNull(dto1)
        assertEquals("user1", dto1.username)
        assertEquals(1, dto1.activeDownloads)
        assertEquals(listOf(1L), dto1.activeGameIds)
        assertEquals(1000L, dto1.totalBytesTransferred)
        assertEquals(100L, dto1.currentBytesPerSecond)
        assertEquals(listOf(100L, 90L, 110L), dto1.bandwidthHistory)

        val dto2 = result.find { it.sessionId == "session-2" }
        assertNotNull(dto2)
        assertEquals("user2", dto2.username)
        assertEquals(2, dto2.activeDownloads)
        assertEquals(listOf(2L, 3L), dto2.activeGameIds)
        assertEquals(2000L, dto2.totalBytesTransferred)
        assertEquals(200L, dto2.currentBytesPerSecond)
    }

    @Test
    fun `getActiveSessions should return empty list when no sessions`() {
        every { sessionBandwidthManager.getStats() } returns emptyMap()

        val result = service.getActiveSessions()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `clearSession should call removeTracker on manager`() {
        service.clearSession("session-123")

        verify(exactly = 1) { sessionBandwidthManager.removeTracker("session-123") }
    }

    @Test
    fun `clearSession should handle non-existent session`() {
        assertDoesNotThrow {
            service.clearSession("non-existent-session")
        }

        verify(exactly = 1) { sessionBandwidthManager.removeTracker("non-existent-session") }
    }

    @Test
    fun `subscribe should return Flux that buffers emissions`() {
        val flux = BandwidthMonitoringService.subscribe()

        assertNotNull(flux)
    }

    @Test
    fun `subscribe should handle multiple subscribers`() {
        val flux1 = BandwidthMonitoringService.subscribe()
        val flux2 = BandwidthMonitoringService.subscribe()

        assertNotNull(flux1)
        assertNotNull(flux2)
        assertNotSame(flux1, flux2)
    }

    @Test
    fun `should emit to multiple subscribers`() {
        val stats = mapOf(
            "session-1" to SessionStats(
                sessionId = "session-1",
                startTime = System.nanoTime(),
                username = "user1",
                remoteIp = "192.168.1.1",
                activeDownloads = 1,
                activeGameIds = setOf(1L),
                totalBytesTransferred = 1000,
                currentBytesPerSecond = 100
            )
        )

        every { sessionBandwidthManager.getStats() } returns stats

        val flux1 = BandwidthMonitoringService.subscribe()
        val flux2 = BandwidthMonitoringService.subscribe()

        // Use concurrent verification for both subscribers
        val verifier1 = StepVerifier.create(flux1)
            .expectNextMatches { it.size == 1 && it[0].size == 1 }
            .thenCancel()
            .verifyLater()

        val verifier2 = StepVerifier.create(flux2)
            .expectNextMatches { it.size == 1 && it[0].size == 1 }
            .thenCancel()
            .verifyLater()

        // Emit after both subscribers are set up
        service.calculateAndEmitBandwidthUpdates()

        // Wait for both verifications to complete
        verifier1.verify(Duration.ofSeconds(5))
        verifier2.verify(Duration.ofSeconds(5))
    }

    @Test
    fun `should handle rapid emissions`() {
        val stats1 = mapOf(
            "session-1" to SessionStats(
                sessionId = "session-1",
                startTime = System.nanoTime(),
                username = "user1",
                remoteIp = "192.168.1.1",
                activeDownloads = 1,
                activeGameIds = setOf(1L),
                totalBytesTransferred = 1000,
                currentBytesPerSecond = 100
            )
        )

        val stats2 = mapOf(
            "session-1" to SessionStats(
                sessionId = "session-1",
                startTime = System.nanoTime(),
                username = "user1",
                remoteIp = "192.168.1.1",
                activeDownloads = 1,
                activeGameIds = setOf(1L),
                totalBytesTransferred = 2000,
                currentBytesPerSecond = 200
            )
        )

        every { sessionBandwidthManager.getStats() } returnsMany listOf(stats1, stats2)

        val flux = BandwidthMonitoringService.subscribe()
        val subscriber = StepVerifier.create(flux)
            .expectNextMatches { it.size == 2 }
            .thenCancel()
            .verifyLater()

        service.calculateAndEmitBandwidthUpdates()
        service.calculateAndEmitBandwidthUpdates()

        subscriber.verify(Duration.ofSeconds(5))
    }

    @Test
    fun `should handle session with null username`() {
        val stats = mapOf(
            "session-1" to SessionStats(
                sessionId = "session-1",
                startTime = System.nanoTime(),
                username = null,
                remoteIp = "192.168.1.1",
                activeDownloads = 1,
                activeGameIds = setOf(1L),
                totalBytesTransferred = 1000,
                currentBytesPerSecond = 100
            )
        )

        every { sessionBandwidthManager.getStats() } returns stats

        val result = service.getActiveSessions()

        assertEquals(1, result.size)
        assertNull(result[0].username)
    }

    @Test
    fun `should handle session with empty activeGameIds`() {
        val stats = mapOf(
            "session-1" to SessionStats(
                sessionId = "session-1",
                startTime = System.nanoTime(),
                username = "user1",
                remoteIp = "192.168.1.1",
                activeDownloads = 0,
                activeGameIds = emptySet(),
                totalBytesTransferred = 1000,
                currentBytesPerSecond = 0
            )
        )

        every { sessionBandwidthManager.getStats() } returns stats

        val result = service.getActiveSessions()

        assertEquals(1, result.size)
        assertTrue(result[0].activeGameIds.isEmpty())
    }

    @Test
    fun `should handle session with large numbers`() {
        val stats = mapOf(
            "session-1" to SessionStats(
                sessionId = "session-1",
                startTime = System.nanoTime(),
                username = "user1",
                remoteIp = "192.168.1.1",
                activeDownloads = Int.MAX_VALUE,
                activeGameIds = setOf(Long.MAX_VALUE),
                totalBytesTransferred = Long.MAX_VALUE,
                currentBytesPerSecond = Long.MAX_VALUE
            )
        )

        every { sessionBandwidthManager.getStats() } returns stats

        val result = service.getActiveSessions()

        assertEquals(1, result.size)
        assertEquals(Int.MAX_VALUE, result[0].activeDownloads)
        assertEquals(Long.MAX_VALUE, result[0].totalBytesTransferred)
    }

    @Test
    fun `should handle exception from manager gracefully`() {
        every { sessionBandwidthManager.getStats() } throws RuntimeException("Test exception")

        assertThrows(RuntimeException::class.java) {
            service.calculateAndEmitBandwidthUpdates()
        }

        verify(exactly = 1) { sessionBandwidthManager.recordAllBandwidthSnapshots() }
    }

    @Test
    fun `should be a Spring Service`() {
        val annotations = BandwidthMonitoringService::class.annotations
        assertTrue(annotations.any { it.annotationClass.simpleName == "Service" })
    }

    @Test
    fun `calculateAndEmitBandwidthUpdates should have Scheduled annotation`() {
        val method = BandwidthMonitoringService::class.java
            .getDeclaredMethod("calculateAndEmitBandwidthUpdates")

        val scheduledAnnotation = method.annotations
            .find { it.annotationClass.simpleName == "Scheduled" }

        assertNotNull(scheduledAnnotation)
    }

    @Test
    fun `should handle concurrent clearSession calls`() {
        val sessionIds = (1..10).map { "session-$it" }

        sessionIds.forEach { sessionId ->
            service.clearSession(sessionId)
        }

        sessionIds.forEach { sessionId ->
            verify(exactly = 1) { sessionBandwidthManager.removeTracker(sessionId) }
        }
    }

    @Test
    fun `should handle bandwidthHistory in DTOs`() {
        val history = listOf(100L, 200L, 150L, 180L, 220L)
        val stats = mapOf(
            "session-1" to SessionStats(
                sessionId = "session-1",
                startTime = System.nanoTime(),
                username = "user1",
                remoteIp = "192.168.1.1",
                activeDownloads = 1,
                activeGameIds = setOf(1L),
                totalBytesTransferred = 1000,
                currentBytesPerSecond = 220,
                bandwidthHistory = history
            )
        )

        every { sessionBandwidthManager.getStats() } returns stats

        val result = service.getActiveSessions()

        assertEquals(1, result.size)
        assertEquals(history, result[0].bandwidthHistory)
    }

    @Test
    fun `should work with real SessionBandwidthManager`() {
        val realManager = SessionBandwidthManager()
        val realService = BandwidthMonitoringService(realManager)

        val tracker = realManager.getTracker("test-session", 100_000)
        tracker.downloadStarted(gameId = 1L, username = "testuser", remoteIp = "127.0.0.1")
        tracker.recordBytes(1000)

        realService.calculateAndEmitBandwidthUpdates()

        val sessions = realService.getActiveSessions()
        assertEquals(1, sessions.size)
        assertEquals("test-session", sessions[0].sessionId)
        assertEquals("testuser", sessions[0].username)
        assertEquals(1, sessions[0].activeDownloads)
        assertEquals(1000L, sessions[0].totalBytesTransferred)

        realService.clearSession("test-session")

        val sessionsAfterClear = realService.getActiveSessions()
        assertEquals(0, sessionsAfterClear.size)
    }
}

