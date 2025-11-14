package org.gameyfin.app.core.download.bandwidth


import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionStatsDtoTest {

    @Test
    fun `should create DTO with all fields`() {
        val now = Instant.now()
        val dto = SessionStatsDto(
            sessionId = "session-123",
            startTime = now,
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 2,
            activeGameIds = listOf(1L, 2L, 3L),
            totalBytesTransferred = 1024000L,
            currentBytesPerSecond = 102400L,
            bandwidthHistory = listOf(100L, 200L, 150L)
        )

        assertEquals("session-123", dto.sessionId)
        assertEquals(now, dto.startTime)
        assertEquals("testuser", dto.username)
        assertEquals("192.168.1.1", dto.remoteIp)
        assertEquals(2, dto.activeDownloads)
        assertEquals(listOf(1L, 2L, 3L), dto.activeGameIds)
        assertEquals(1024000L, dto.totalBytesTransferred)
        assertEquals(102400L, dto.currentBytesPerSecond)
        assertEquals(listOf(100L, 200L, 150L), dto.bandwidthHistory)
    }

    @Test
    fun `should handle null username`() {
        val dto = SessionStatsDto(
            sessionId = "session-123",
            startTime = Instant.now(),
            username = null,
            remoteIp = "192.168.1.1",
            activeDownloads = 1,
            activeGameIds = listOf(1L),
            totalBytesTransferred = 1000L,
            currentBytesPerSecond = 100L
        )

        assertNull(dto.username)
    }

    @Test
    fun `should handle empty activeGameIds`() {
        val dto = SessionStatsDto(
            sessionId = "session-123",
            startTime = Instant.now(),
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 0,
            activeGameIds = emptyList(),
            totalBytesTransferred = 0L,
            currentBytesPerSecond = 0L
        )

        assertTrue(dto.activeGameIds.isEmpty())
    }

    @Test
    fun `should handle empty bandwidthHistory`() {
        val dto = SessionStatsDto(
            sessionId = "session-123",
            startTime = Instant.now(),
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 1,
            activeGameIds = listOf(1L),
            totalBytesTransferred = 1000L,
            currentBytesPerSecond = 100L,
            bandwidthHistory = emptyList()
        )

        assertTrue(dto.bandwidthHistory.isEmpty())
    }

    @Test
    fun `should support data class copy`() {
        val original = SessionStatsDto(
            sessionId = "session-123",
            startTime = Instant.now(),
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 1,
            activeGameIds = listOf(1L),
            totalBytesTransferred = 1000L,
            currentBytesPerSecond = 100L
        )

        val copied = original.copy(username = "newuser")

        assertEquals("newuser", copied.username)
        assertEquals(original.sessionId, copied.sessionId)
        assertEquals(original.remoteIp, copied.remoteIp)
    }

    @Test
    fun `should support equality comparison`() {
        val now = Instant.now()
        val dto1 = SessionStatsDto(
            sessionId = "session-123",
            startTime = now,
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 1,
            activeGameIds = listOf(1L),
            totalBytesTransferred = 1000L,
            currentBytesPerSecond = 100L
        )

        val dto2 = SessionStatsDto(
            sessionId = "session-123",
            startTime = now,
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 1,
            activeGameIds = listOf(1L),
            totalBytesTransferred = 1000L,
            currentBytesPerSecond = 100L
        )

        assertEquals(dto1, dto2)
        assertEquals(dto1.hashCode(), dto2.hashCode())
    }

    @Test
    fun `should detect inequality when fields differ`() {
        val now = Instant.now()
        val dto1 = SessionStatsDto(
            sessionId = "session-123",
            startTime = now,
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 1,
            activeGameIds = listOf(1L),
            totalBytesTransferred = 1000L,
            currentBytesPerSecond = 100L
        )

        val dto2 = dto1.copy(activeDownloads = 2)

        assertNotEquals(dto1, dto2)
    }

    @Test
    fun `should handle large numbers`() {
        val dto = SessionStatsDto(
            sessionId = "session-123",
            startTime = Instant.now(),
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = Int.MAX_VALUE,
            activeGameIds = listOf(Long.MAX_VALUE),
            totalBytesTransferred = Long.MAX_VALUE,
            currentBytesPerSecond = Long.MAX_VALUE
        )

        assertEquals(Int.MAX_VALUE, dto.activeDownloads)
        assertEquals(Long.MAX_VALUE, dto.activeGameIds[0])
        assertEquals(Long.MAX_VALUE, dto.totalBytesTransferred)
        assertEquals(Long.MAX_VALUE, dto.currentBytesPerSecond)
    }

    @Test
    fun `should handle special IP addresses`() {
        val testIps = listOf(
            "0.0.0.0",
            "127.0.0.1",
            "255.255.255.255",
            "::1",
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
            "unknown"
        )

        testIps.forEach { ip ->
            val dto = SessionStatsDto(
                sessionId = "session-123",
                startTime = Instant.now(),
                username = "testuser",
                remoteIp = ip,
                activeDownloads = 1,
                activeGameIds = listOf(1L),
                totalBytesTransferred = 1000L,
                currentBytesPerSecond = 100L
            )

            assertEquals(ip, dto.remoteIp)
        }
    }

    @Test
    fun `should handle long bandwidthHistory`() {
        val history = (1..100).map { it * 100L }
        val dto = SessionStatsDto(
            sessionId = "session-123",
            startTime = Instant.now(),
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 1,
            activeGameIds = listOf(1L),
            totalBytesTransferred = 1000L,
            currentBytesPerSecond = 100L,
            bandwidthHistory = history
        )

        assertEquals(100, dto.bandwidthHistory.size)
        assertEquals(100L, dto.bandwidthHistory[0])
        assertEquals(10000L, dto.bandwidthHistory[99])
    }

    @Test
    fun `should handle special characters in sessionId`() {
        val specialIds = listOf(
            "session-123",
            "SESSION_456",
            "session.789",
            "session:abc",
            "!@#$%^&*()"
        )

        specialIds.forEach { sessionId ->
            val dto = SessionStatsDto(
                sessionId = sessionId,
                startTime = Instant.now(),
                username = "testuser",
                remoteIp = "192.168.1.1",
                activeDownloads = 1,
                activeGameIds = listOf(1L),
                totalBytesTransferred = 1000L,
                currentBytesPerSecond = 100L
            )

            assertEquals(sessionId, dto.sessionId)
        }
    }

    @Test
    fun `should handle multiple activeGameIds`() {
        val gameIds = (1L..1000L).toList()
        val dto = SessionStatsDto(
            sessionId = "session-123",
            startTime = Instant.now(),
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 1000,
            activeGameIds = gameIds,
            totalBytesTransferred = 1000000L,
            currentBytesPerSecond = 100000L
        )

        assertEquals(1000, dto.activeGameIds.size)
        assertEquals(1L, dto.activeGameIds[0])
        assertEquals(1000L, dto.activeGameIds[999])
    }
}

