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

        // Should be called at least once
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
    fun `write empty byte array should call throttle`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        throttledStream.write(byteArrayOf())

        verify(exactly = 1) { sessionTracker.throttle(0) }
        assertEquals("", underlyingOutputStream.toString())
    }

    @Test
    fun `write byte array with zero length should throttle with zero bytes`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        val data = "Hello".toByteArray()
        throttledStream.write(data, 0, 0)

        verify(exactly = 1) { sessionTracker.throttle(0) }
        assertEquals("", underlyingOutputStream.toString())
    }

    @Test
    fun `write should throttle exact byte count without chunking`() {
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        // Write exactly 1024 KB
        val data = ByteArray(1024 * 1024) { 0 }
        throttledStream.write(data)

        // Should be throttled exactly once for the entire write
        verify(exactly = 1) { sessionTracker.throttle(1024L * 1024) }
        assertEquals(1024 * 1024, underlyingOutputStream.size())
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
        val bytesPerSecond = 100_000L // 100 KB/s
        val realTracker = SessionBandwidthTracker("test-session", bytesPerSecond)

        // Consume the initial burst from RateLimiter (RateLimiter allows up to 1 second of burst)
        // We need to consume more than the burst capacity before throttling kicks in
        val burstData = ByteArray(200_000) { 0 } // 200 KB = 2 seconds worth at 100 KB/s
        val burstStream = SessionThrottledOutputStream(
            ByteArrayOutputStream(),
            realTracker,
            gameId = 999L,
            username = "testuser",
            remoteIp = "127.0.0.1"
        )
        burstStream.write(burstData)
        burstStream.close()

        // Now create the actual test stream - this should be properly throttled
        throttledStream = SessionThrottledOutputStream(
            underlyingOutputStream,
            realTracker,
            gameId = 1L,
            username = "testuser",
            remoteIp = "127.0.0.1"
        )

        assertEquals(1, realTracker.activeDownloads.get())

        val startTime = System.nanoTime()
        val data = ByteArray(200_000) { it.toByte() } // 200 KB
        throttledStream.write(data)
        val elapsed = (System.nanoTime() - startTime) / 1_000_000_000.0

        // Should take at least 1.8 seconds to transfer 200 KB at 100 KB/s
        // Using 1.7 to account for timing variations
        assertTrue(elapsed >= 1.7, "Expected at least 1.7 seconds but was $elapsed")
        assertEquals(400_000L, realTracker.totalBytesTransferred)

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

        // Write large data with offset/length
        val data = ByteArray(1200 * 1024) { it.toByte() }
        throttledStream.write(data, 10000, 800000)

        // Should throttle exactly once for the specified length
        verify(exactly = 1) { sessionTracker.throttle(800000L) }
        assertEquals(800000, underlyingOutputStream.size())
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
        verify(exactly = 2) { sessionTracker.throttle(1) }  // Two single bytes
        verify(exactly = 2) { sessionTracker.throttle(2) }  // Two 2-byte arrays
    }
}

