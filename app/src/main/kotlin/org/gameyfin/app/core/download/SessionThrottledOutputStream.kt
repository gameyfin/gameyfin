package org.gameyfin.app.core.download

import java.io.OutputStream

/**
 * An OutputStream wrapper that limits bandwidth on a per-session basis.
 * Multiple concurrent downloads by the same session will share the bandwidth limit.
 *
 * @param outputStream The underlying output stream to write to
 * @param sessionTracker The session-wide bandwidth tracker
 */
class SessionThrottledOutputStream(
    private val outputStream: OutputStream,
    private val sessionTracker: SessionBandwidthTracker
) : OutputStream() {

    // Buffer size for optimal I/O performance
    private val optimalBufferSize = 64 * 1024

    init {
        sessionTracker.downloadStarted()
    }

    override fun write(b: Int) {
        sessionTracker.throttle(1)
        outputStream.write(b)
    }

    override fun write(b: ByteArray) {
        write(b, 0, b.size)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        // Write in chunks to maintain accurate throttling across concurrent downloads
        var remaining = len
        var offset = off

        while (remaining > 0) {
            val chunkSize = minOf(remaining, optimalBufferSize)
            sessionTracker.throttle(chunkSize.toLong())
            outputStream.write(b, offset, chunkSize)
            remaining -= chunkSize
            offset += chunkSize
        }
    }

    override fun flush() {
        outputStream.flush()
    }

    override fun close() {
        try {
            outputStream.close()
        } finally {
            sessionTracker.downloadCompleted()
        }
    }
}

