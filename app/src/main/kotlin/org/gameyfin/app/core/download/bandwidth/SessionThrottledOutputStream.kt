package org.gameyfin.app.core.download.bandwidth

import java.io.OutputStream

/**
 * An OutputStream wrapper that limits bandwidth on a per-session basis.
 * Multiple concurrent downloads by the same session will share the bandwidth limit.
 *
 * @param outputStream The underlying output stream to write to
 * @param sessionTracker The session-wide bandwidth tracker
 * @param gameId The ID of the game being downloaded (optional)
 * @param username The username of the person downloading (optional)
 * @param remoteIp The remote IP address of the client (optional)
 */
class SessionThrottledOutputStream(
    private val outputStream: OutputStream,
    private val sessionTracker: SessionBandwidthTracker,
    private val gameId: Long? = null,
    private val username: String? = null,
    private val remoteIp: String? = null
) : OutputStream() {

    // 512 KB provides good balance between throughput and throttling responsiveness
    private val optimalBufferSize = 512 * 1024

    init {
        sessionTracker.downloadStarted(gameId, username, remoteIp)
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
            sessionTracker.downloadCompleted(gameId)
        }
    }
}

