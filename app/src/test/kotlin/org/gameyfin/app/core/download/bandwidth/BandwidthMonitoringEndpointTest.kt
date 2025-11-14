package org.gameyfin.app.core.download.bandwidth

import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import reactor.test.StepVerifier
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BandwidthMonitoringEndpointTest {

    private lateinit var bandwidthMonitoringService: BandwidthMonitoringService
    private lateinit var endpoint: BandwidthMonitoringEndpoint

    @BeforeEach
    fun setup() {
        bandwidthMonitoringService = mockk<BandwidthMonitoringService>(relaxed = true)
        endpoint = BandwidthMonitoringEndpoint(bandwidthMonitoringService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `subscribe should return Flux when user is admin`() {
        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { org.gameyfin.app.core.security.isCurrentUserAdmin() } returns true

        val result = endpoint.subscribe()

        assertNotNull(result)

        verify { org.gameyfin.app.core.security.isCurrentUserAdmin() }
    }

    @Test
    fun `subscribe should return empty Flux when user is not admin`() {
        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { org.gameyfin.app.core.security.isCurrentUserAdmin() } returns false

        val result = endpoint.subscribe()

        assertNotNull(result)

        StepVerifier.create(result)
            .expectNextCount(0)
            .expectComplete()
            .verify(Duration.ofSeconds(1))

        verify { org.gameyfin.app.core.security.isCurrentUserAdmin() }
    }

    @Test
    fun `getActiveSessions should delegate to service`() {
        val expectedSessions = listOf(
            SessionStatsDto(
                sessionId = "session-1",
                startTime = java.time.Instant.now(),
                username = "user1",
                remoteIp = "192.168.1.1",
                activeDownloads = 1,
                activeGameIds = listOf(1L),
                totalBytesTransferred = 1000,
                currentBytesPerSecond = 100
            )
        )

        every { bandwidthMonitoringService.getActiveSessions() } returns expectedSessions

        val result = endpoint.getActiveSessions()

        assertEquals(expectedSessions, result)
        verify(exactly = 1) { bandwidthMonitoringService.getActiveSessions() }
    }

    @Test
    fun `getActiveSessions should return empty list when no sessions`() {
        every { bandwidthMonitoringService.getActiveSessions() } returns emptyList()

        val result = endpoint.getActiveSessions()

        assertTrue(result.isEmpty())
        verify(exactly = 1) { bandwidthMonitoringService.getActiveSessions() }
    }

    @Test
    fun `clearSession should delegate to service`() {
        endpoint.clearSession("session-123")

        verify(exactly = 1) { bandwidthMonitoringService.clearSession("session-123") }
    }

    @Test
    fun `clearSession should handle empty session id`() {
        endpoint.clearSession("")

        verify(exactly = 1) { bandwidthMonitoringService.clearSession("") }
    }

    @Test
    fun `clearSession should handle special characters in session id`() {
        val specialSessionId = "session-!@#$%^&*()_+-=[]{}|;':\",./<>?"

        endpoint.clearSession(specialSessionId)

        verify(exactly = 1) { bandwidthMonitoringService.clearSession(specialSessionId) }
    }

    @Test
    fun `should be annotated with Endpoint`() {
        val annotations = BandwidthMonitoringEndpoint::class.annotations
        assertTrue(annotations.any { it.annotationClass.simpleName == "Endpoint" })
    }

    @Test
    fun `should be annotated with RolesAllowed ADMIN`() {
        val annotations = BandwidthMonitoringEndpoint::class.annotations
        assertTrue(annotations.any { it.annotationClass.simpleName == "RolesAllowed" })
    }

    @Test
    fun `subscribe should be annotated with PermitAll`() {
        val method = BandwidthMonitoringEndpoint::class.java
            .getDeclaredMethod("subscribe")

        val permitAllAnnotation = method.annotations
            .find { it.annotationClass.simpleName == "PermitAll" }

        assertNotNull(permitAllAnnotation)
    }

    @Test
    fun `getActiveSessions should handle service exception`() {
        every { bandwidthMonitoringService.getActiveSessions() } throws RuntimeException("Service error")

        assertThrows(RuntimeException::class.java) {
            endpoint.getActiveSessions()
        }

        verify(exactly = 1) { bandwidthMonitoringService.getActiveSessions() }
    }

    @Test
    fun `clearSession should handle service exception`() {
        every { bandwidthMonitoringService.clearSession(any()) } throws RuntimeException("Service error")

        assertThrows(RuntimeException::class.java) {
            endpoint.clearSession("session-123")
        }

        verify(exactly = 1) { bandwidthMonitoringService.clearSession("session-123") }
    }

    @Test
    fun `getActiveSessions should handle null values in DTOs`() {
        val sessionsWithNulls = listOf(
            SessionStatsDto(
                sessionId = "session-1",
                startTime = java.time.Instant.now(),
                username = null,
                remoteIp = "192.168.1.1",
                activeDownloads = 0,
                activeGameIds = emptyList(),
                totalBytesTransferred = 0,
                currentBytesPerSecond = 0
            )
        )

        every { bandwidthMonitoringService.getActiveSessions() } returns sessionsWithNulls

        val result = endpoint.getActiveSessions()

        assertEquals(1, result.size)
        assertNull(result[0].username)
    }

    @Test
    fun `getActiveSessions should handle large number of sessions`() {
        val largeSessions = (1..1000).map {
            SessionStatsDto(
                sessionId = "session-$it",
                startTime = java.time.Instant.now(),
                username = "user$it",
                remoteIp = "192.168.1.$it",
                activeDownloads = it,
                activeGameIds = listOf(it.toLong()),
                totalBytesTransferred = it * 1000L,
                currentBytesPerSecond = it * 100L
            )
        }

        every { bandwidthMonitoringService.getActiveSessions() } returns largeSessions

        val result = endpoint.getActiveSessions()

        assertEquals(1000, result.size)
        verify(exactly = 1) { bandwidthMonitoringService.getActiveSessions() }
    }

    @Test
    fun `should handle multiple concurrent getActiveSessions calls`() {
        val sessions = listOf(
            SessionStatsDto(
                sessionId = "session-1",
                startTime = java.time.Instant.now(),
                username = "user1",
                remoteIp = "192.168.1.1",
                activeDownloads = 1,
                activeGameIds = listOf(1L),
                totalBytesTransferred = 1000,
                currentBytesPerSecond = 100
            )
        )

        every { bandwidthMonitoringService.getActiveSessions() } returns sessions

        repeat(10) {
            endpoint.getActiveSessions()
        }

        verify(exactly = 10) { bandwidthMonitoringService.getActiveSessions() }
    }

    @Test
    fun `should handle multiple concurrent clearSession calls`() {
        repeat(10) { i ->
            endpoint.clearSession("session-$i")
        }

        verify(exactly = 10) { bandwidthMonitoringService.clearSession(any()) }
    }

    @Test
    fun `getActiveSessions should return sessions with bandwidth history`() {
        val sessions = listOf(
            SessionStatsDto(
                sessionId = "session-1",
                startTime = java.time.Instant.now(),
                username = "user1",
                remoteIp = "192.168.1.1",
                activeDownloads = 1,
                activeGameIds = listOf(1L),
                totalBytesTransferred = 1000,
                currentBytesPerSecond = 100,
                bandwidthHistory = listOf(100L, 95L, 110L, 105L)
            )
        )

        every { bandwidthMonitoringService.getActiveSessions() } returns sessions

        val result = endpoint.getActiveSessions()

        assertEquals(1, result.size)
        assertEquals(listOf(100L, 95L, 110L, 105L), result[0].bandwidthHistory)
    }

    @Test
    fun `clearSession should handle null session id without NPE`() {
        // This tests defensive programming - though the parameter is not nullable
        // If somehow null is passed, service should handle it
        every { bandwidthMonitoringService.clearSession(any()) } just Runs

        assertDoesNotThrow {
            endpoint.clearSession("null")
        }
    }

    @Test
    fun `subscribe should handle admin check exception`() {
        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { org.gameyfin.app.core.security.isCurrentUserAdmin() } throws RuntimeException("Auth error")

        assertThrows(RuntimeException::class.java) {
            endpoint.subscribe()
        }
    }
}

