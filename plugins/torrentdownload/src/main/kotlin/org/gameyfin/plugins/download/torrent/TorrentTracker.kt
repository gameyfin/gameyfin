package org.gameyfin.plugins.download.torrent

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A simple BitTorrent tracker implementation using HTTP protocol.
 * Implements the basic announce/scrape protocol as defined in BEP 3.
 * Supports hybrid torrents (BEP-52) by grouping v1 and v2 swarms using the key parameter (BEP-7).
 *
 * The key parameter remains constant for a peer across the same torrent's v1/v2 variants,
 * allowing the tracker to link related swarms and provide better peer discovery.
 */
class TorrentTracker(
    private val port: Int,
    private val announceInterval: Int
) {
    private val log = LoggerFactory.getLogger(TorrentTracker::class.java)
    private var server: HttpServer? = null

    // Map of info_hash -> peers
    private val torrents = ConcurrentHashMap<String, MutableSet<Peer>>()

    // Map of key -> set of info_hashes (for linking hybrid torrent swarms)
    // The key parameter is per-torrent and stays the same across v1/v2 variants,
    // allowing us to group related swarms of the same hybrid torrent
    private val hybridTorrentGroups = ConcurrentHashMap<String, MutableSet<String>>()

    data class Peer(
        val peerId: String,
        val ip: String,
        val port: Int,
        var uploaded: Long = 0,
        var downloaded: Long = 0,
        var left: Long = 0,
        var lastSeen: Long = System.currentTimeMillis(),
        val key: String? = null  // BEP-7 key parameter - per-torrent identifier for grouping hybrid variants
    )

    fun start() {
        server = HttpServer.create(InetSocketAddress(port), 0).apply {
            createContext("/announce") { exchange ->
                try {
                    handleAnnounce(exchange)
                } catch (e: Exception) {
                    log.error("Unhandled error in announce handler", e)
                    try {
                        respondBencodedError(exchange, "Internal server error")
                    } catch (_: Exception) {
                        // Ignore errors when trying to send error response
                    }
                } finally {
                    exchange.close()
                }
            }

            createContext("/scrape") { exchange ->
                try {
                    handleScrape(exchange)
                } catch (e: Exception) {
                    log.error("Unhandled error in scrape handler", e)
                    try {
                        respondBencodedError(exchange, "Internal server error")
                    } catch (_: Exception) {
                        // Ignore errors when trying to send error response
                    }
                } finally {
                    exchange.close()
                }
            }

            executor = Executors.newSingleThreadExecutor()
            start()
        }

        log.info("Tracker started on port $port")

        // Start cleanup task
        startCleanupTask()
    }

    fun stop() {
        val currentServer = server
        server = null

        currentServer?.stop(2)

        // Shutdown the executor service
        currentServer?.executor?.let { executor ->
            (executor as? java.util.concurrent.ExecutorService)?.let {
                it.shutdown()
                try {
                    if (!it.awaitTermination(5, TimeUnit.SECONDS)) {
                        it.shutdownNow()
                    }
                } catch (_: InterruptedException) {
                    it.shutdownNow()
                }
            }
        }

        log.info("Tracker stopped")
    }

    private fun bytesToHex(bytes: String): String {
        return bytes.toByteArray(Charsets.ISO_8859_1).joinToString("") {
            "%02x".format(it.toInt() and 0xFF)
        }
    }

    private fun handleAnnounce(exchange: HttpExchange) {
        try {
            // Get raw query string from URI - we need to parse it ourselves to handle binary data
            // Using .query would give us a UTF-8 decoded string which corrupts binary info_hash
            val rawQuery = exchange.requestURI.rawQuery ?: ""

            val params = parseQueryString(rawQuery)

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
            val key = params["key"] // BEP-7 key parameter - per-torrent identifier (same for v1/v2 variants)

            // Get client IP from params or use remote address
            val ip = params["ip"] ?: run {
                val remoteAddress = exchange.remoteAddress.address.hostAddress
                log.debug("Param 'ip' not provided, falling back to remote host address ($remoteAddress) for peer $peerId")
                remoteAddress
            }

            // Track hybrid torrent grouping if key is provided
            if (!key.isNullOrBlank()) {
                val relatedHashes = hybridTorrentGroups.computeIfAbsent(key) {
                    ConcurrentHashMap.newKeySet()
                }
                relatedHashes.add(infoHash)

                log.debug("Linked info_hash ${bytesToHex(infoHash)} with key $key (group has ${relatedHashes.size} hashes)")
            }

            // Get or create torrent peer list
            val peers = torrents.computeIfAbsent(infoHash) {
                log.debug("New torrent tracked: ${bytesToHex(infoHash)}")
                ConcurrentHashMap.newKeySet()
            }

            // Handle event
            when (event) {
                "stopped" -> {
                    peers.removeIf { it.peerId == peerId }
                    log.debug("Removed peer $peerId ($ip) from torrent ${bytesToHex(infoHash)}")

                    // Also remove from related hybrid torrent swarms if key is provided
                    if (!key.isNullOrBlank()) {
                        hybridTorrentGroups[key]?.forEach { relatedHash ->
                            if (relatedHash != infoHash) {
                                torrents[relatedHash]?.removeIf { it.peerId == peerId && it.key == key }
                            }
                        }
                    }
                }

                else -> {
                    val existingPeer = peers.find { it.peerId == peerId }

                    if (existingPeer != null) {
                        peers.remove(existingPeer)
                        peers.add(Peer(peerId, ip, port, uploaded, downloaded, left, key = key))
                        log.debug("Updated peer $peerId ($ip) for torrent ${bytesToHex(infoHash)}")
                    } else {
                        peers.add(Peer(peerId, ip, port, uploaded, downloaded, left, key = key))
                        log.debug("Added peer $peerId ($ip) to torrent ${bytesToHex(infoHash)}")
                    }

                    // Sync peer to related hybrid torrent swarms if key is provided
                    if (!key.isNullOrBlank()) {
                        hybridTorrentGroups[key]?.forEach { relatedHash ->
                            if (relatedHash != infoHash) {
                                val relatedPeers = torrents.computeIfAbsent(relatedHash) {
                                    ConcurrentHashMap.newKeySet()
                                }
                                relatedPeers.removeIf { it.peerId == peerId && it.key == key }
                                relatedPeers.add(Peer(peerId, ip, port, uploaded, downloaded, left, key = key))
                            }
                        }
                    }
                }
            }

            // Build peer list from this swarm and related hybrid swarms
            val allPeers = mutableSetOf<Peer>()
            allPeers.addAll(peers)

            // Include peers from related hybrid torrent swarms
            if (!key.isNullOrBlank()) {
                hybridTorrentGroups[key]?.forEach { relatedHash ->
                    torrents[relatedHash]?.let { allPeers.addAll(it) }
                }
            }

            // Deduplicate by peerId and exclude the requesting peer
            val peerList = allPeers
                .distinctBy { it.peerId }
                .filter { it.peerId != peerId }
                .take(50)

            // Calculate stats across all related swarms
            val uniquePeers = allPeers.distinctBy { it.peerId }

            // Send response
            respondBencodedAnnounce(
                exchange,
                interval = announceInterval,
                complete = uniquePeers.count { it.left == 0L },
                incomplete = uniquePeers.count { it.left > 0L },
                peers = peerList
            )

        } catch (e: Exception) {
            log.error("Error handling announce", e)
            respondBencodedError(exchange, "Internal server error")
        }
    }

    private fun handleScrape(exchange: HttpExchange) {
        try {
            val params = parseQueryString(exchange.requestURI.rawQuery ?: "")
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
        exchange.responseBody.write(bytes)
        exchange.responseBody.flush()
    }

    private fun parseQueryString(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()

        return query.split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = URLDecoder.decode(parts[0], "UTF-8")
                    // Use ISO-8859-1 for binary parameters (info_hash, peer_id)
                    // to preserve binary data without UTF-8 corruption
                    val value = if (key == "info_hash" || key == "peer_id") {
                        urlDecodeToBytes(parts[1])
                    } else {
                        URLDecoder.decode(parts[1], "UTF-8")
                    }
                    key to value
                } else {
                    null
                }
            }
            .toMap()
    }

    /**
     * Decode a URL-encoded string to raw bytes, preserving binary data.
     * Unlike URLDecoder.decode(), this uses ISO-8859-1 to preserve binary data.
     */
    private fun urlDecodeToBytes(encoded: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < encoded.length) {
            when {
                encoded[i] == '%' && i + 2 < encoded.length -> {
                    // Decode %XX to a byte
                    val hex = encoded.substring(i + 1, i + 3)
                    bytes.add(hex.toInt(16).toByte())
                    i += 3
                }

                encoded[i] == '+' -> {
                    // Plus sign represents space in URL encoding
                    bytes.add(' '.code.toByte())
                    i++
                }

                else -> {
                    // Regular character
                    bytes.add(encoded[i].code.toByte())
                    i++
                }
            }
        }
        // Convert bytes to String using ISO-8859-1 (preserves binary data)
        return String(bytes.toByteArray(), Charsets.ISO_8859_1)
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

