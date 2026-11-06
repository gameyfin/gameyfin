package org.gameyfin.app.core.download.bandwidth

import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.test.assertEquals

class SessionMonitoredOutputStreamTest {

    private lateinit var underlyingOutputStream: ByteArrayOutputStream
    private lateinit var sessionTracker: SessionBandwidthTracker
    private lateinit var monitoredStream: SessionMonitoredOutputStream

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
        monitoredStream = SessionMonitoredOutputStream(
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
        monitoredStream = SessionMonitoredOutputStream(
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
    fun `write single byte should record bytes and write to underlying stream`() {
        monitoredStream = SessionMonitoredOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        monitoredStream.write(65) // 'A'

        verify(exactly = 1) { sessionTracker.recordBytes(1) }
        assertEquals("A", underlyingOutputStream.toString())
    }

    @Test
    fun `write multiple single bytes should record each byte`() {
        monitoredStream = SessionMonitoredOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        monitoredStream.write(65) // 'A'
        monitoredStream.write(66) // 'B'
        monitoredStream.write(67) // 'C'

        verify(exactly = 3) { sessionTracker.recordBytes(1) }
        assertEquals("ABC", underlyingOutputStream.toString())
    }

    @Test
    fun `write byte array should record bytes and write to underlying stream`() {
        monitoredStream = SessionMonitoredOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        val data = "Hello World".toByteArray()
        monitoredStream.write(data)

        verify(exactly = 1) { sessionTracker.recordBytes(11) }
        assertEquals("Hello World", underlyingOutputStream.toString())
    }

    @Test
    fun `write byte array with offset and length should record correct bytes`() {
        monitoredStream = SessionMonitoredOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        val data = "Hello World".toByteArray()
        monitoredStream.write(data, 6, 5) // "World"

        verify(exactly = 1) { sessionTracker.recordBytes(5) }
        assertEquals("World", underlyingOutputStream.toString())
    }

    @Test
    fun `write empty byte array should record zero bytes`() {
        monitoredStream = SessionMonitoredOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        monitoredStream.write(byteArrayOf())

        verify(exactly = 1) { sessionTracker.recordBytes(0) }
        assertEquals("", underlyingOutputStream.toString())
    }

    @Test
    fun `write byte array with zero length should record zero bytes`() {
        monitoredStream = SessionMonitoredOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        val data = "Hello".toByteArray()
        monitoredStream.write(data, 0, 0)

        verify(exactly = 1) { sessionTracker.recordBytes(0) }
        assertEquals("", underlyingOutputStream.toString())
    }

    @Test
    fun `write large byte array should record all bytes`() {
        monitoredStream = SessionMonitoredOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        val data = ByteArray(1024 * 1024) { it.toByte() } // 1 MB
        monitoredStream.write(data)

        verify(exactly = 1) { sessionTracker.recordBytes(1024 * 1024) }
        assertEquals(1024 * 1024, underlyingOutputStream.size())
    }

    @Test
    fun `flush should flush underlying stream`() {
        val mockOutputStream = mockk<ByteArrayOutputStream>(relaxed = true)
        monitoredStream = SessionMonitoredOutputStream(
            mockOutputStream,
            sessionTracker
        )

        monitoredStream.flush()

        verify(exactly = 1) { mockOutputStream.flush() }
    }

    @Test
    fun `close should close underlying stream and call downloadCompleted`() {
        monitoredStream = SessionMonitoredOutputStream(
            underlyingOutputStream,
            sessionTracker,
            gameId = 42L
        )

        monitoredStream.close()

        verify(exactly = 1) { sessionTracker.downloadCompleted(42L) }
        // ByteArrayOutputStream.close() is a no-op, but we can verify it doesn't throw
    }

    @Test
    fun `close should call downloadCompleted with null gameId`() {
        monitoredStream = SessionMonitoredOutputStream(
            underlyingOutputStream,
            sessionTracker,
            gameId = null
        )

        monitoredStream.close()

        verify(exactly = 1) { sessionTracker.downloadCompleted(null) }
    }

    @Test
    fun `close should call downloadCompleted even if underlying stream throws`() {
        val failingOutputStream = mockk<ByteArrayOutputStream>(relaxed = true)
        every { failingOutputStream.close() } throws IOException("Test exception")

        monitoredStream = SessionMonitoredOutputStream(
            failingOutputStream,
            sessionTracker,
            gameId = 42L
        )

        assertThrows(IOException::class.java) {
            monitoredStream.close()
        }

        verify(exactly = 1) { sessionTracker.downloadCompleted(42L) }
    }

    @Test
    fun `should handle multiple writes correctly`() {
        monitoredStream = SessionMonitoredOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        monitoredStream.write("Hello ".toByteArray())
        monitoredStream.write("World".toByteArray())
        monitoredStream.write(33) // '!'

        verify(exactly = 1) { sessionTracker.recordBytes(6) }
        verify(exactly = 1) { sessionTracker.recordBytes(5) }
        verify(exactly = 1) { sessionTracker.recordBytes(1) }
        assertEquals("Hello World!", underlyingOutputStream.toString())
    }

    @Test
    fun `should work with real tracker`() {
        val realTracker = SessionBandwidthTracker("test-session", 1_000_000)
        monitoredStream = SessionMonitoredOutputStream(
            underlyingOutputStream,
            realTracker,
            gameId = 1L,
            username = "testuser",
            remoteIp = "127.0.0.1"
        )

        assertEquals(1, realTracker.activeDownloads.get())
        assertEquals("testuser", realTracker.username)
        assertEquals("127.0.0.1", realTracker.remoteIp)

        val data = "Test data".toByteArray()
        monitoredStream.write(data)

        assertEquals(data.size.toLong(), realTracker.totalBytesTransferred)
        assertEquals("Test data", underlyingOutputStream.toString())

        monitoredStream.close()

        assertEquals(0, realTracker.activeDownloads.get())
    }

    @Test
    fun `should handle write operations after recordBytes calls`() {
        monitoredStream = SessionMonitoredOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        // Write some data
        monitoredStream.write("First".toByteArray())
        monitoredStream.write("Second".toByteArray())

        // Order matters
        verifyOrder {
            sessionTracker.downloadStarted(null, null, null)
            sessionTracker.recordBytes(5)
            sessionTracker.recordBytes(6)
        }
    }

    @Test
    fun `should handle edge case of writing at array boundaries`() {
        monitoredStream = SessionMonitoredOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        val data = "0123456789".toByteArray()

        // Write from start
        monitoredStream.write(data, 0, 5)
        assertEquals("01234", underlyingOutputStream.toString())

        underlyingOutputStream.reset()

        // Write to end
        monitoredStream.write(data, 5, 5)
        assertEquals("56789", underlyingOutputStream.toString())

        verify(exactly = 2) { sessionTracker.recordBytes(5) }
    }

    @Test
    fun `should propagate write exceptions from underlying stream`() {
        val failingOutputStream = object : java.io.OutputStream() {
            override fun write(b: Int) {
                throw IOException("Write failed")
            }
        }

        monitoredStream = SessionMonitoredOutputStream(
            failingOutputStream,
            sessionTracker
        )

        assertThrows(IOException::class.java) {
            monitoredStream.write(65)
        }

        // recordBytes should still be called before the exception
        verify(exactly = 1) { sessionTracker.recordBytes(1) }
    }

    @Test
    fun `should propagate flush exceptions from underlying stream`() {
        val failingOutputStream = object : java.io.OutputStream() {
            override fun write(b: Int) {}
            override fun flush() {
                throw IOException("Flush failed")
            }
        }

        monitoredStream = SessionMonitoredOutputStream(
            failingOutputStream,
            sessionTracker
        )

        assertThrows(IOException::class.java) {
            monitoredStream.flush()
        }
    }

    @Test
    fun `should handle writing bytes with special values`() {
        monitoredStream = SessionMonitoredOutputStream(
            underlyingOutputStream,
            sessionTracker
        )

        // Write boundary values
        monitoredStream.write(0)
        monitoredStream.write(255)
        monitoredStream.write(-1) // Should be treated as 255

        verify(exactly = 3) { sessionTracker.recordBytes(1) }
    }

    @Test
    fun `multiple close calls should only call downloadCompleted once`() {
        monitoredStream = SessionMonitoredOutputStream(
            underlyingOutputStream,
            sessionTracker,
            gameId = 42L
        )

        monitoredStream.close()
        monitoredStream.close()
        monitoredStream.close()

        // ByteArrayOutputStream allows multiple close calls
        // downloadCompleted should be called for each close
        verify(exactly = 3) { sessionTracker.downloadCompleted(42L) }
    }
}

