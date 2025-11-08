package org.gameyfin.app.core.download.bandwidth

import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.test.assertEquals

class SessionThrottledOutputStreamTest {

    private lateinit var underlyingOutputStream: ByteArrayOutputStream
    private lateinit var sessionTracker: SessionBandwidthTracker
    private lateinit var throttledStream: SessionThrottledOutputStream

    @BeforeEach
    fun setup() {
        underlyingOutputStream = ByteArrayOutputStream()
        sessionTracker = mockk<SessionBandwidthTracker>(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `init should call downloadStarted with all parameters`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker,
            gameId = 42L,
            username = "testuser",
            remoteIp = "192.168.1.1"
        )

        verify(exactly = 1) {
            sessionTracker.downloadStarted(42L, "testuser", "192.168.1.1")
        }
    }

    @Test
    fun `init should call downloadStarted with null parameters`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker,
            gameId = null,
            username = null,
            remoteIp = null
        )

        verify(exactly = 1) {
            sessionTracker.downloadStarted(null, null, null)
        }
    }

    @Test
    fun `write single byte should throttle and write to underlying stream`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        throttledStream.write(65) // 'A'

        verify(exactly = 1) { sessionTracker.throttle(1) }
        assertEquals("A", underlyingOutputStream.toString())
    }

    @Test
    fun `write multiple single bytes should throttle each byte`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        throttledStream.write(65) // 'A'
        throttledStream.write(66) // 'B'
        throttledStream.write(67) // 'C'

        verify(exactly = 3) { sessionTracker.throttle(1) }
        assertEquals("ABC", underlyingOutputStream.toString())
    }

    @Test
    fun `write byte array should throttle and write to underlying stream`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        val data = "Hello World".toByteArray()
        throttledStream.write(data)

        // Should be called at least once (may be chunked)
        verify(atLeast = 1) { sessionTracker.throttle(any()) }
        assertEquals("Hello World", underlyingOutputStream.toString())
    }

    @Test
    fun `write byte array with offset and length should throttle correct bytes`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        val data = "Hello World".toByteArray()
        throttledStream.write(data, 6, 5) // "World"

        verify(atLeast = 1) { sessionTracker.throttle(any()) }
        assertEquals("World", underlyingOutputStream.toString())
    }

    @Test
    fun `write empty byte array should not throttle`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        throttledStream.write(byteArrayOf())

        verify(exactly = 0) { sessionTracker.throttle(any()) }
        assertEquals("", underlyingOutputStream.toString())
    }

    @Test
    fun `write byte array with zero length should not throttle`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        val data = "Hello".toByteArray()
        throttledStream.write(data, 0, 0)

        verify(exactly = 0) { sessionTracker.throttle(any()) }
        assertEquals("", underlyingOutputStream.toString())
    }

    @Test
    fun `write large byte array should be chunked`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        val data = ByteArray(200 * 1024) { it.toByte() } // 200 KB
        throttledStream.write(data)

        // With 64KB chunks, 200KB should require at least 3 chunks
        verify(atLeast = 3) { sessionTracker.throttle(any()) }
        assertEquals(200 * 1024, underlyingOutputStream.size())
    }

    @Test
    fun `write should respect optimal buffer size for chunking`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        // Write exactly 128 KB (2 * 64KB chunks)
        val data = ByteArray(128 * 1024) { 0 }
        throttledStream.write(data)

        // Should be throttled in at least 2 chunks
        verify(atLeast = 2) { sessionTracker.throttle(any()) }

        // Each chunk should be <= 64KB
        val slots = mutableListOf<Long>()
        verify(atLeast = 2) { sessionTracker.throttle(capture(slots)) }
        assertTrue(slots.all { it <= 64 * 1024 })
    }

    @Test
    fun `flush should flush underlying stream`() {
        val mockOutputStream = mockk<ByteArrayOutputStream>(relaxed = true)
        throttledStream = SessionThrottledOutputStream(
            mockOutputStream,
            sessionTracker
        )

        throttledStream.flush()

        verify(exactly = 1) { mockOutputStream.flush() }
    }

    @Test
    fun `close should close underlying stream and call downloadCompleted`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker,
            gameId = 42L
        )

        throttledStream.close()

        verify(exactly = 1) { sessionTracker.downloadCompleted(42L) }
    }

    @Test
    fun `close should call downloadCompleted with null gameId`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker,
            gameId = null
        )

        throttledStream.close()

        verify(exactly = 1) { sessionTracker.downloadCompleted(null) }
    }

    @Test
    fun `close should call downloadCompleted even if underlying stream throws`() {
        val failingOutputStream = mockk<ByteArrayOutputStream>(relaxed = true)
        every { failingOutputStream.close() } throws IOException("Test exception")

        throttledStream = SessionThrottledOutputStream(
            failingOutputStream,
            sessionTracker,
            gameId = 42L
        )

        assertThrows(IOException::class.java) {
            throttledStream.close()
        }

        verify(exactly = 1) { sessionTracker.downloadCompleted(42L) }
    }

    @Test
    fun `should handle multiple writes correctly`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        throttledStream.write("Hello ".toByteArray())
        throttledStream.write("World".toByteArray())
        throttledStream.write(33) // '!'

        verify(atLeast = 3) { sessionTracker.throttle(any()) }
        assertEquals("Hello World!", underlyingOutputStream.toString())
    }

    @Test
    fun `should work with real tracker and throttle bandwidth`() {
        val bytesPerSecond = 10_000L // 10 KB/s
        val realTracker = SessionBandwidthTracker("test-session", bytesPerSecond)
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            realTracker,
            gameId = 1L,
            username = "testuser",
            remoteIp = "127.0.0.1"
        )

        assertEquals(1, realTracker.activeDownloads.get())

        val startTime = System.nanoTime()
        val data = ByteArray(20_000) { it.toByte() } // 20 KB
        throttledStream.write(data)
        val elapsed = (System.nanoTime() - startTime) / 1_000_000_000.0

        // Should take at least 1.5 seconds to transfer 20 KB at 10 KB/s
        // Using 1.3 to account for timing variations
        assertTrue(elapsed >= 1.3, "Expected at least 1.3 seconds but was $elapsed")
        assertEquals(20_000L, realTracker.totalBytesTransferred)

        throttledStream.close()
        assertEquals(0, realTracker.activeDownloads.get())
    }

    @Test
    fun `should handle write operations in correct order`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        throttledStream.write("First".toByteArray())
        throttledStream.write("Second".toByteArray())

        verifyOrder {
            sessionTracker.downloadStarted(null, null, null)
            sessionTracker.throttle(any())
            sessionTracker.throttle(any())
        }
    }

    @Test
    fun `should handle edge case of writing at array boundaries`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        val data = "0123456789".toByteArray()

        throttledStream.write(data, 0, 5)
        assertEquals("01234", underlyingOutputStream.toString())

        underlyingOutputStream.reset()

        throttledStream.write(data, 5, 5)
        assertEquals("56789", underlyingOutputStream.toString())

        verify(atLeast = 2) { sessionTracker.throttle(any()) }
    }

    @Test
    fun `should propagate write exceptions from underlying stream`() {
        val failingOutputStream = object : java.io.OutputStream() {
            override fun write(b: Int) {
                throw IOException("Write failed")
            }
        }

        throttledStream = SessionThrottledOutputStream(
            failingOutputStream,
            sessionTracker
        )

        assertThrows(IOException::class.java) {
            throttledStream.write(65)
        }

        // throttle should still be called before the exception
        verify(exactly = 1) { sessionTracker.throttle(1) }
    }

    @Test
    fun `should propagate flush exceptions from underlying stream`() {
        val failingOutputStream = object : java.io.OutputStream() {
            override fun write(b: Int) {}
            override fun flush() {
                throw IOException("Flush failed")
            }
        }

        throttledStream = SessionThrottledOutputStream(
            failingOutputStream,
            sessionTracker
        )

        assertThrows(IOException::class.java) {
            throttledStream.flush()
        }
    }

    @Test
    fun `should handle writing bytes with special values`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        throttledStream.write(0)
        throttledStream.write(255)
        throttledStream.write(-1) // Should be treated as 255

        verify(exactly = 3) { sessionTracker.throttle(1) }
    }

    @Test
    fun `should chunk large writes correctly`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        // Write exactly 3 chunks worth (192 KB)
        val data = ByteArray(192 * 1024) { 0 }
        throttledStream.write(data)

        val capturedSizes = mutableListOf<Long>()
        verify(atLeast = 3) { sessionTracker.throttle(capture(capturedSizes)) }

        // Verify total bytes throttled equals data size
        assertEquals(data.size.toLong(), capturedSizes.sum())

        // Verify all chunks are <= 64KB
        assertTrue(capturedSizes.all { it <= 64 * 1024 })
    }

    @Test
    fun `should handle small writes without chunking`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        val data = ByteArray(100) { 0 }
        throttledStream.write(data)

        // Small write should be a single throttle call
        verify(exactly = 1) { sessionTracker.throttle(100) }
    }

    @Test
    fun `should handle write with offset and length correctly`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        // Write large data with offset/length that spans multiple chunks
        val data = ByteArray(200 * 1024) { it.toByte() }
        throttledStream.write(data, 10000, 150000)

        // Should write exactly 150000 bytes
        val capturedSizes = mutableListOf<Long>()
        verify(atLeast = 2) { sessionTracker.throttle(capture(capturedSizes)) }
        assertEquals(150000L, capturedSizes.sum())
        assertEquals(150000, underlyingOutputStream.size())
    }

    @Test
    fun `multiple close calls should call downloadCompleted multiple times`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker,
            gameId = 42L
        )

        throttledStream.close()
        throttledStream.close()
        throttledStream.close()

        // ByteArrayOutputStream allows multiple close calls
        verify(exactly = 3) { sessionTracker.downloadCompleted(42L) }
    }

    @Test
    fun `should handle alternating single byte and array writes`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        throttledStream.write(65) // 'A'
        throttledStream.write("BC".toByteArray())
        throttledStream.write(68) // 'D'
        throttledStream.write("EF".toByteArray())

        assertEquals("ABCDEF", underlyingOutputStream.toString())
        verify(atLeast = 4) { sessionTracker.throttle(any()) }
    }

    @Test
    fun `should handle exact chunk boundary`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        // Write exactly 64KB (one chunk)
        val data = ByteArray(64 * 1024) { 0 }
        throttledStream.write(data)

        verify(exactly = 1) { sessionTracker.throttle(64L * 1024) }
        assertEquals(64 * 1024, underlyingOutputStream.size())
    }

    @Test
    fun `should handle chunk boundary plus one byte`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        // Write 64KB + 1 byte (should be 2 chunks)
        val data = ByteArray(64 * 1024 + 1) { 0 }
        throttledStream.write(data)

        val capturedSizes = mutableListOf<Long>()
        verify(exactly = 2) { sessionTracker.throttle(capture(capturedSizes)) }
        assertEquals(64L * 1024, capturedSizes[0])
        assertEquals(1L, capturedSizes[1])
    }
}

