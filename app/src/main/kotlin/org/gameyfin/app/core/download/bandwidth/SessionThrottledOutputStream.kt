package org.gameyfin.app.core.download.bandwidth

import java.io.FilterOutputStream
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
    outputStream: OutputStream,
    private val sessionTracker: SessionBandwidthTracker,
    private val gameId: Long? = null,
    private val username: String? = null,
    private val remoteIp: String? = null
) : FilterOutputStream(outputStream) {

    init {
        sessionTracker.downloadStarted(gameId, username, remoteIp)
    }

    override fun write(b: Int) {
        sessionTracker.throttle(1)
        out.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        sessionTracker.throttle(len.toLong())
        out.write(b, off, len)
    }

    override fun close() {
        try {
            super.close()
        } finally {
            sessionTracker.downloadCompleted(gameId)
        }
    }
}

