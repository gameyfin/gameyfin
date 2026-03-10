package org.gameyfin.app.core.download.bandwidth

import java.io.FilterOutputStream
import java.io.OutputStream

/**
 * An OutputStream wrapper that tracks bandwidth usage without throttling.
 * Used when bandwidth limiting is disabled, but we still want real-time statistics.
 *
 * @param outputStream The underlying output stream to write to
 * @param sessionTracker The session-wide bandwidth tracker
 * @param gameId The ID of the game being downloaded (optional)
 * @param username The username of the person downloading (optional)
 * @param remoteIp The remote IP address of the client (optional)
 */
class SessionMonitoredOutputStream(
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
        sessionTracker.recordBytes(1)
        out.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        sessionTracker.recordBytes(len.toLong())
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

