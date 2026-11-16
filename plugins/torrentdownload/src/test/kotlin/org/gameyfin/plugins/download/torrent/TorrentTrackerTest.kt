package org.gameyfin.plugins.download.torrent

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TorrentTrackerTest {
    @Test
    fun `announce updates and stops peers across hybrid swarms`() = withTracker { base ->
        val firstHash = "a".repeat(20)
        val secondHash = "b".repeat(20)
        val firstPeer = "1".repeat(20)
        val secondPeer = "2".repeat(20)

        fun announce(hash: String, peer: String, extra: String = ""): String = request(
            "$base/announce?info_hash=$hash&peer_id=$peer&port=6881&ip=127.0.0.1&key=hybrid&$extra"
        )

        assertTrue(announce(firstHash, firstPeer, "left=10").contains("5:peers0:"))
        announce(secondHash, firstPeer, "left=10")
        val joined = announce(secondHash, secondPeer, "left=0")
        assertTrue(joined.contains("8:completei1e10:incompletei1e"))
        assertTrue(joined.contains("5:peers6:" + compactPeer(1, 6881)))

        announce(firstHash, firstPeer, "left=0&ip=127.0.0.2&port=6882")
        val updated = announce(secondHash, secondPeer, "left=0")
        assertTrue(updated.contains("8:completei2e10:incompletei0e"))
        assertTrue(updated.contains("5:peers6:" + compactPeer(2, 6882)))

        val scraped = request("$base/scrape")
        for (hash in listOf(firstHash, secondHash)) {
            assertTrue(scraped.contains("20:${hash}d8:completei2e10:downloadedi2e10:incompletei0ee"))
        }

        val stopped = announce(firstHash, firstPeer, "event=stopped")
        assertTrue(stopped.contains("8:completei1e10:incompletei0e"))
        assertTrue(stopped.contains("5:peers6:" + compactPeer(1, 6881)))
        val remaining = announce(secondHash, secondPeer, "left=0")
        assertTrue(remaining.contains("5:peers0:"))
        assertTrue(remaining.contains("8:completei1e10:incompletei0e"))
        assertTrue(request("$base/scrape?info_hash=$secondHash").contains("20:$secondHash"))
        assertTrue(!request("$base/scrape?info_hash=$secondHash").contains("20:$firstHash"))
    }

    @Test
    fun `invalid announce parameters return bencoded errors`() = withTracker { base ->
        assertEquals("d14:failure reason17:Missing info_hashe", request("$base/announce"))
        assertEquals("d14:failure reason15:Missing peer_ide", request("$base/announce?info_hash=hash"))
        assertEquals(
            "d14:failure reason23:Missing or invalid porte",
            request("$base/announce?info_hash=hash&peer_id=peer&port=invalid")
        )
        request("$base/announce?info_hash=hash&peer_id=first&port=6881&ip=bad.bad.bad.bad")
        assertEquals(
            "d14:failure reason21:Internal server errore",
            request("$base/announce?info_hash=hash&peer_id=second&port=6881&ip=127.0.0.1")
        )
    }

    private fun compactPeer(lastOctet: Int, port: Int): String = String(
        byteArrayOf(127, 0, 0, lastOctet.toByte(), (port shr 8).toByte(), port.toByte()),
        Charsets.ISO_8859_1
    )

    private fun request(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        try {
            assertEquals(200, connection.responseCode)
            assertEquals("text/plain", connection.getHeaderField("Content-Type"))
            return connection.inputStream.use { String(it.readBytes(), Charsets.ISO_8859_1) }
        } finally {
            connection.disconnect()
        }
    }

    private fun withTracker(block: (String) -> Unit) {
        val tracker = TorrentTracker(0, 1800)
        // Exercise real HTTP handlers without starting the unrelated five-minute cleanup thread.
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            for ((path, handler) in mapOf("/announce" to "handleAnnounce", "/scrape" to "handleScrape")) {
                val method = TorrentTracker::class.java.getDeclaredMethod(handler, HttpExchange::class.java)
                method.isAccessible = true
                server.createContext(path) { exchange ->
                    exchange.use {
                        method.invoke(tracker, exchange)
                    }
                }
            }
            server.start()
            block("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }
}
