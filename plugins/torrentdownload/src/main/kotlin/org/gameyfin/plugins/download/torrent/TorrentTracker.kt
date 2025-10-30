package org.gameyfin.plugins.download.torrent

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A simple BitTorrent tracker implementation using HTTP protocol.
 * Implements the basic announce/scrape protocol as defined in BEP 3.
 */
class TorrentTracker(
    private val port: Int,
    private val announceInterval: Int = 1800 // 30 minutes
) {
    private val log = LoggerFactory.getLogger(TorrentTracker::class.java)
    private var server: HttpServer? = null

    // Map of info_hash -> peers
    private val torrents = ConcurrentHashMap<String, MutableSet<Peer>>()

    data class Peer(
        val peerId: String,
        val ip: String,
        val port: Int,
        var uploaded: Long = 0,
        var downloaded: Long = 0,
        var left: Long = 0,
        var lastSeen: Long = System.currentTimeMillis()
    )

    fun start() {
        server = HttpServer.create(InetSocketAddress(port), 0).apply {
            createContext("/announce") { exchange ->
                handleAnnounce(exchange)
            }

            createContext("/scrape") { exchange ->
                handleScrape(exchange)
            }

            executor = Executors.newFixedThreadPool(4)
            start()
        }

        log.info("Tracker started on port $port")

        // Start cleanup task
        startCleanupTask()
    }

    fun stop() {
        server?.stop(2)
        server = null
        log.info("Tracker stopped")
    }

    fun getAnnounceUrl(): String {
        val hostname = try {
            InetAddress.getLocalHost().hostAddress
        } catch (e: Exception) {
            "localhost"
        }
        return "http://$hostname:$port/announce"
    }

    private fun handleAnnounce(exchange: HttpExchange) {
        try {
            val params = parseQueryString(exchange.requestURI.query ?: "")

            // Required parameters
            val infoHash = params["info_hash"] ?: run {
                respondBencodedError(exchange, "Missing info_hash")
                return
            }
            val peerId = params["peer_id"] ?: run {
                respondBencodedError(exchange, "Missing peer_id")
                return
            }
            val port = params["port"]?.toIntOrNull() ?: run {
                respondBencodedError(exchange, "Missing or invalid port")
                return
            }

            // Optional parameters
            val uploaded = params["uploaded"]?.toLongOrNull() ?: 0
            val downloaded = params["downloaded"]?.toLongOrNull() ?: 0
            val left = params["left"]?.toLongOrNull() ?: 0
            val event = params["event"] // started, completed, stopped

            // Get client IP
            val ip = params["ip"] ?: exchange.remoteAddress.address.hostAddress

            // Get or create torrent peer list
            val peers = torrents.computeIfAbsent(infoHash) { ConcurrentHashMap.newKeySet() }

            // Handle event
            when (event) {
                "stopped" -> {
                    peers.removeIf { it.peerId == peerId }
                }

                else -> {
                    // Add or update peer
                    peers.removeIf { it.peerId == peerId }
                    peers.add(Peer(peerId, ip, port, uploaded, downloaded, left))
                }
            }

            // Build peer list (exclude the requesting peer)
            val peerList = peers.filter { it.peerId != peerId }.take(50)

            // Send response
            respondBencodedAnnounce(
                exchange,
                interval = announceInterval,
                complete = peers.count { it.left == 0L },
                incomplete = peers.count { it.left > 0L },
                peers = peerList
            )

        } catch (e: Exception) {
            log.error("Error handling announce", e)
            respondBencodedError(exchange, "Internal server error")
        }
    }

    private fun handleScrape(exchange: HttpExchange) {
        try {
            val params = parseQueryString(exchange.requestURI.query ?: "")
            val infoHashes = params.entries
                .filter { it.key == "info_hash" }
                .map { it.value }

            val response = buildString {
                append("d5:filesd")

                if (infoHashes.isEmpty()) {
                    // Scrape all torrents
                    torrents.forEach { (infoHash, peers) ->
                        appendTorrentStats(infoHash, peers)
                    }
                } else {
                    // Scrape specific torrents
                    infoHashes.forEach { infoHash ->
                        torrents[infoHash]?.let { peers ->
                            appendTorrentStats(infoHash, peers)
                        }
                    }
                }

                append("ee")
            }

            respondBytes(exchange, response.toByteArray(Charsets.ISO_8859_1))

        } catch (e: Exception) {
            log.error("Error handling scrape", e)
            respondBencodedError(exchange, "Internal server error")
        }
    }

    private fun StringBuilder.appendTorrentStats(infoHash: String, peers: Set<Peer>) {
        val complete = peers.count { it.left == 0L }
        val incomplete = peers.count { it.left > 0L }
        val downloaded = peers.count() // Total number of times completed

        append("20:") // info_hash is always 20 bytes
        append(infoHash)
        append("d")
        append("8:completei${complete}e")
        append("10:downloadedi${downloaded}e")
        append("10:incompletei${incomplete}e")
        append("e")
    }

    private fun respondBencodedError(exchange: HttpExchange, message: String) {
        val response = "d14:failure reason${message.length}:${message}e"
        respondBytes(exchange, response.toByteArray(Charsets.ISO_8859_1))
    }

    private fun respondBencodedAnnounce(
        exchange: HttpExchange,
        interval: Int,
        complete: Int,
        incomplete: Int,
        peers: List<Peer>
    ) {
        val response = buildString {
            append("d")
            append("8:intervali${interval}e")
            append("8:completei${complete}e")
            append("10:incompletei${incomplete}e")

            // Compact peer list (binary format)
            append("5:peers")
            val peerBytes = ByteBuffer.allocate(peers.size * 6)
            peers.forEach { peer ->
                val ipParts = peer.ip.split(".")
                if (ipParts.size == 4) {
                    ipParts.forEach { peerBytes.put(it.toInt().toByte()) }
                    peerBytes.putShort(peer.port.toShort())
                }
            }
            val compactPeers = peerBytes.array().copyOf(peerBytes.position())
            append("${compactPeers.size}:")
            append(String(compactPeers, Charsets.ISO_8859_1))

            append("e")
        }

        respondBytes(exchange, response.toByteArray(Charsets.ISO_8859_1))
    }

    private fun respondBytes(exchange: HttpExchange, bytes: ByteArray) {
        exchange.responseHeaders.set("Content-Type", "text/plain")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun parseQueryString(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()

        return query.split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = URLDecoder.decode(parts[0], "UTF-8")
                    val value = URLDecoder.decode(parts[1], "UTF-8")
                    key to value
                } else {
                    null
                }
            }
            .toMap()
    }

    private fun startCleanupTask() {
        // Simple cleanup - in production you'd want a scheduled executor
        Thread {
            while (server != null) {
                try {
                    Thread.sleep(TimeUnit.MINUTES.toMillis(5))
                    cleanupStalePeers()
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    log.error("Error in cleanup task", e)
                }
            }
        }.apply {
            isDaemon = true
            name = "tracker-cleanup"
            start()
        }
    }

    private fun cleanupStalePeers() {
        val staleThreshold = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)

        torrents.forEach { (infoHash, peers) ->
            val initialSize = peers.size
            peers.removeIf { it.lastSeen < staleThreshold }
            val removed = initialSize - peers.size

            if (removed > 0) {
                log.debug("Removed $removed stale peers from torrent $infoHash")
            }

            if (peers.isEmpty()) {
                torrents.remove(infoHash)
            }
        }
    }
}

