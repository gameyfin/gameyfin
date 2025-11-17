package org.gameyfin.app.core.download.bandwidth

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionStatsTest {

    @BeforeEach
    fun setup() {
        // Mock the nanoTimeToInstant function
        mockkStatic("org.gameyfin.app.core.UtilsKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should create SessionStats with all fields`() {
        val startTime = System.nanoTime()
        val stats = SessionStats(
            sessionId = "session-123",
            startTime = startTime,
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 2,
            activeGameIds = setOf(1L, 2L, 3L),
            totalBytesTransferred = 1024000L,
            currentBytesPerSecond = 102400L,
            bandwidthHistory = listOf(100L, 200L, 150L)
        )

        assertEquals("session-123", stats.sessionId)
        assertEquals(startTime, stats.startTime)
        assertEquals("testuser", stats.username)
        assertEquals("192.168.1.1", stats.remoteIp)
        assertEquals(2, stats.activeDownloads)
        assertEquals(setOf(1L, 2L, 3L), stats.activeGameIds)
        assertEquals(1024000L, stats.totalBytesTransferred)
        assertEquals(102400L, stats.currentBytesPerSecond)
        assertEquals(listOf(100L, 200L, 150L), stats.bandwidthHistory)
    }

    @Test
    fun `should handle null username`() {
        val stats = SessionStats(
            sessionId = "session-123",
            startTime = System.nanoTime(),
            username = null,
            remoteIp = "192.168.1.1",
            activeDownloads = 1,
            activeGameIds = setOf(1L),
            totalBytesTransferred = 1000L,
            currentBytesPerSecond = 100L
        )

        assertNull(stats.username)
    }

    @Test
    fun `should handle empty activeGameIds`() {
        val stats = SessionStats(
            sessionId = "session-123",
            startTime = System.nanoTime(),
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 0,
            activeGameIds = emptySet(),
            totalBytesTransferred = 0L,
            currentBytesPerSecond = 0L
        )

        assertTrue(stats.activeGameIds.isEmpty())
    }

    @Test
    fun `should handle empty bandwidthHistory`() {
        val stats = SessionStats(
            sessionId = "session-123",
            startTime = System.nanoTime(),
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 1,
            activeGameIds = setOf(1L),
            totalBytesTransferred = 1000L,
            currentBytesPerSecond = 100L,
            bandwidthHistory = emptyList()
        )

        assertTrue(stats.bandwidthHistory.isEmpty())
    }

    @Test
    fun `toDto should convert SessionStats to SessionStatsDto`() {
        val startTime = System.nanoTime()
        val expectedInstant = Instant.now()

        every { org.gameyfin.app.core.nanoTimeToInstant(startTime) } returns expectedInstant

        val stats = SessionStats(
            sessionId = "session-123",
            startTime = startTime,
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 2,
            activeGameIds = setOf(1L, 2L, 3L),
            totalBytesTransferred = 1024000L,
            currentBytesPerSecond = 102400L,
            bandwidthHistory = listOf(100L, 200L, 150L)
        )

        val dto = stats.toDto()

        assertEquals("session-123", dto.sessionId)
        assertEquals(expectedInstant, dto.startTime)
        assertEquals("testuser", dto.username)
        assertEquals("192.168.1.1", dto.remoteIp)
        assertEquals(2, dto.activeDownloads)
        assertEquals(listOf(1L, 2L, 3L), dto.activeGameIds)
        assertEquals(1024000L, dto.totalBytesTransferred)
        assertEquals(102400L, dto.currentBytesPerSecond)
        assertEquals(listOf(100L, 200L, 150L), dto.bandwidthHistory)
    }

    @Test
    fun `toDto should convert Set to List for activeGameIds`() {
        val startTime = System.nanoTime()
        every { org.gameyfin.app.core.nanoTimeToInstant(startTime) } returns Instant.now()

        val stats = SessionStats(
            sessionId = "session-123",
            startTime = startTime,
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 3,
            activeGameIds = setOf(3L, 1L, 2L), // Set is unordered
            totalBytesTransferred = 1000L,
            currentBytesPerSecond = 100L
        )

        val dto = stats.toDto()

        assertEquals(3, dto.activeGameIds.size)
        assertTrue(dto.activeGameIds.containsAll(listOf(1L, 2L, 3L)))
    }

    @Test
    fun `toDtos should convert collection of SessionStats to list of DTOs`() {
        val startTime = System.nanoTime()
        every { org.gameyfin.app.core.nanoTimeToInstant(startTime) } returns Instant.now()

        val statsList = listOf(
            SessionStats(
                sessionId = "session-1",
                startTime = startTime,
                username = "user1",
                remoteIp = "192.168.1.1",
                activeDownloads = 1,
                activeGameIds = setOf(1L),
                totalBytesTransferred = 1000L,
                currentBytesPerSecond = 100L
            ),
            SessionStats(
                sessionId = "session-2",
                startTime = startTime,
                username = "user2",
                remoteIp = "192.168.1.2",
                activeDownloads = 2,
                activeGameIds = setOf(2L, 3L),
                totalBytesTransferred = 2000L,
                currentBytesPerSecond = 200L
            )
        )

        val dtos = statsList.toDtos()

        assertEquals(2, dtos.size)
        assertEquals("session-1", dtos[0].sessionId)
        assertEquals("session-2", dtos[1].sessionId)
        assertEquals("user1", dtos[0].username)
        assertEquals("user2", dtos[1].username)
    }

    @Test
    fun `toDtos should handle empty collection`() {
        val dtos = emptyList<SessionStats>().toDtos()

        assertTrue(dtos.isEmpty())
    }

    @Test
    fun `toDtos should work with Set of SessionStats`() {
        val startTime = System.nanoTime()
        every { org.gameyfin.app.core.nanoTimeToInstant(startTime) } returns Instant.now()

        val statsSet = setOf(
            SessionStats(
                sessionId = "session-1",
                startTime = startTime,
                username = "user1",
                remoteIp = "192.168.1.1",
                activeDownloads = 1,
                activeGameIds = setOf(1L),
                totalBytesTransferred = 1000L,
                currentBytesPerSecond = 100L
            )
        )

        val dtos = statsSet.toDtos()

        assertEquals(1, dtos.size)
        assertEquals("session-1", dtos[0].sessionId)
    }

    @Test
    fun `should support data class copy`() {
        val original = SessionStats(
            sessionId = "session-123",
            startTime = System.nanoTime(),
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 1,
            activeGameIds = setOf(1L),
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
        val startTime = System.nanoTime()
        val stats1 = SessionStats(
            sessionId = "session-123",
            startTime = startTime,
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 1,
            activeGameIds = setOf(1L),
            totalBytesTransferred = 1000L,
            currentBytesPerSecond = 100L
        )

        val stats2 = SessionStats(
            sessionId = "session-123",
            startTime = startTime,
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 1,
            activeGameIds = setOf(1L),
            totalBytesTransferred = 1000L,
            currentBytesPerSecond = 100L
        )

        assertEquals(stats1, stats2)
        assertEquals(stats1.hashCode(), stats2.hashCode())
    }

    @Test
    fun `should handle large numbers`() {
        val stats = SessionStats(
            sessionId = "session-123",
            startTime = Long.MAX_VALUE,
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = Int.MAX_VALUE,
            activeGameIds = setOf(Long.MAX_VALUE),
            totalBytesTransferred = Long.MAX_VALUE,
            currentBytesPerSecond = Long.MAX_VALUE
        )

        assertEquals(Long.MAX_VALUE, stats.startTime)
        assertEquals(Int.MAX_VALUE, stats.activeDownloads)
        assertEquals(Long.MAX_VALUE, stats.totalBytesTransferred)
        assertEquals(Long.MAX_VALUE, stats.currentBytesPerSecond)
    }

    @Test
    fun `activeGameIds should maintain uniqueness as Set`() {
        val stats = SessionStats(
            sessionId = "session-123",
            startTime = System.nanoTime(),
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 3,
            activeGameIds = setOf(1L, 2L, 3L, 2L, 1L), // Duplicates in constructor
            totalBytesTransferred = 1000L,
            currentBytesPerSecond = 100L
        )

        assertEquals(3, stats.activeGameIds.size)
        assertTrue(stats.activeGameIds.containsAll(setOf(1L, 2L, 3L)))
    }

    @Test
    fun `toDto should handle null username in conversion`() {
        val startTime = System.nanoTime()
        every { org.gameyfin.app.core.nanoTimeToInstant(startTime) } returns Instant.now()

        val stats = SessionStats(
            sessionId = "session-123",
            startTime = startTime,
            username = null,
            remoteIp = "192.168.1.1",
            activeDownloads = 1,
            activeGameIds = setOf(1L),
            totalBytesTransferred = 1000L,
            currentBytesPerSecond = 100L
        )

        val dto = stats.toDto()

        assertNull(dto.username)
    }

    @Test
    fun `toDtos should handle stats with mixed null and non-null usernames`() {
        val startTime = System.nanoTime()
        every { org.gameyfin.app.core.nanoTimeToInstant(startTime) } returns Instant.now()

        val statsList = listOf(
            SessionStats(
                sessionId = "session-1",
                startTime = startTime,
                username = null,
                remoteIp = "192.168.1.1",
                activeDownloads = 1,
                activeGameIds = setOf(1L),
                totalBytesTransferred = 1000L,
                currentBytesPerSecond = 100L
            ),
            SessionStats(
                sessionId = "session-2",
                startTime = startTime,
                username = "user2",
                remoteIp = "192.168.1.2",
                activeDownloads = 1,
                activeGameIds = setOf(2L),
                totalBytesTransferred = 2000L,
                currentBytesPerSecond = 200L
            )
        )

        val dtos = statsList.toDtos()

        assertEquals(2, dtos.size)
        assertNull(dtos[0].username)
        assertEquals("user2", dtos[1].username)
    }

    @Test
    fun `bandwidthHistory should preserve order`() {
        val history = listOf(300L, 100L, 200L, 50L, 400L)
        val stats = SessionStats(
            sessionId = "session-123",
            startTime = System.nanoTime(),
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 1,
            activeGameIds = setOf(1L),
            totalBytesTransferred = 1000L,
            currentBytesPerSecond = 100L,
            bandwidthHistory = history
        )

        assertEquals(history, stats.bandwidthHistory)
        assertEquals(300L, stats.bandwidthHistory[0])
        assertEquals(400L, stats.bandwidthHistory[4])
    }

    @Test
    fun `toDto should preserve bandwidthHistory order`() {
        val startTime = System.nanoTime()
        every { org.gameyfin.app.core.nanoTimeToInstant(startTime) } returns Instant.now()

        val history = listOf(300L, 100L, 200L)
        val stats = SessionStats(
            sessionId = "session-123",
            startTime = startTime,
            username = "testuser",
            remoteIp = "192.168.1.1",
            activeDownloads = 1,
            activeGameIds = setOf(1L),
            totalBytesTransferred = 1000L,
            currentBytesPerSecond = 100L,
            bandwidthHistory = history
        )

        val dto = stats.toDto()

        assertEquals(history, dto.bandwidthHistory)
    }
}

