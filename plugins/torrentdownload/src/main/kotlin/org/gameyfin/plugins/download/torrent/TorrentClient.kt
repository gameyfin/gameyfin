package org.gameyfin.plugins.download.torrent

import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.TorrentHandle.QUERY_DISTRIBUTED_COPIES
import com.frostwire.jlibtorrent.TorrentHandle.QUERY_NAME
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.swig.settings_pack.string_types
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * A BitTorrent client implementation using jlibtorrent.
 * Handles torrent session management, seeding, and monitoring.
 */
class TorrentClient(
    private val listenPort: Int,
    private val externalHost: String?,
    private val dhtEnabled: Boolean,
    private val lsdEnabled: Boolean,
    private val stopSeedingWhenComplete: Boolean
) {
    private val log = LoggerFactory.getLogger(TorrentClient::class.java)

    private var session: SessionManager? = null
    private var monitorExecutor: ScheduledExecutorService? = null
    private var alertProcessorExecutor: ScheduledExecutorService? = null

    // Lock for synchronizing access to the native SessionManager
    // This prevents concurrent access issues that can cause JVM crashes in Linux/Docker
    private object SessionLock

    // Queue for alerts from native callback - processed on Java thread to avoid native thread crashes
    private val alertQueue = ConcurrentLinkedQueue<Alert<*>>()

    companion object {
        private const val INTERNAL_PEER_ID_PREFIX = "-GF0001-"
    }

    fun start() {
        session = initSession()

        // Start alert processor before monitoring task to handle alerts from session start
        startAlertProcessor()

        if (stopSeedingWhenComplete) {
            startMonitoringTask()
        }

        log.info("TorrentClient started")
    }

    fun stop() {
        monitorExecutor?.shutdown()
        try {
            monitorExecutor?.awaitTermination(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            monitorExecutor?.shutdownNow()
        }
        monitorExecutor = null

        alertProcessorExecutor?.shutdown()
        try {
            alertProcessorExecutor?.awaitTermination(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            alertProcessorExecutor?.shutdownNow()
        }
        alertProcessorExecutor = null

        synchronized(SessionLock) {
            session?.stop()
            session = null
        }

        // Clear any remaining alerts
        alertQueue.clear()

        log.info("TorrentClient stopped")
    }

    fun addTorrent(torrentFile: Path, gameFile: Path) {
        val ti = TorrentInfo(torrentFile.toFile())

        // For seeding, we need to use the parent directory as the save path
        val savePath = gameFile.parent.toFile()

        synchronized(SessionLock) {
            // Check if torrent is already in session
            val existingHandle = session?.find(ti)
            if (existingHandle != null && existingHandle.isValid) {
                log.debug("Torrent ${ti.name()} is already in session, skipping")
                return
            }

            // Verify file access before adding to session
            if (!Files.isReadable(gameFile)) {
                log.error("Cannot read game file for seeding: $gameFile - check file permissions")
                throw IllegalStateException("Game file is not readable: $gameFile")
            }

            try {
                // Use SessionManager's download method - it will seed the files in the save directory
                session?.download(ti, savePath)
                log.info("Added torrent to session for seeding: ${ti.name()} from $savePath")
            } catch (e: Exception) {
                log.error("Failed to add torrent to session for seeding: ${ti.name()}", e)
                throw e
            }
        }
    }

    private fun initSession(): SessionManager {
        synchronized(SessionLock) {
            // Return existing session if already initialized
            session?.let { return it }

            // Initialize jlibtorrent session
            val sessionManager = SessionManager()

            // Configure session settings with custom peer ID
            val settingsPack = sessionManager.settings() ?: SettingsPack()

            // Set custom peer ID prefix for our internal client
            // This allows us to identify this specific client if needed
            settingsPack.peerFingerprint = INTERNAL_PEER_ID_PREFIX.toByteArray()

            // Configure interfaces
            settingsPack.listenInterfaces("0.0.0.0:$listenPort,[::]:$listenPort")

            // Configure announce IP if externalHost is set
            if (externalHost != null && externalHost.isNotBlank()) {
                try {
                    val resolvedIp = InetAddress.getByName(externalHost).hostAddress
                    settingsPack.setString(string_types.announce_ip.swigValue(), resolvedIp)
                    log.info("Configured client announce IP to: $resolvedIp (from external host: $externalHost)")
                } catch (e: Exception) {
                    log.error("Failed to resolve external host '$externalHost' for client IP", e)
                }
            } else {
                log.info("No external host override set; using default announce IP behavior")
            }

            // Configure DHT
            settingsPack.isEnableDht = dhtEnabled

            // Configure Local Peer Discovery
            settingsPack.isEnableLsd = lsdEnabled

            // Add alert listener to log errors and connection attempts
            // IMPORTANT: This callback is invoked from native libtorrent threads!
            // We must NOT perform any complex operations here or access the JVM directly,
            // as this can cause crashes in Linux/Docker environments where native threads
            // may not be properly attached to the JVM.
            // Instead, we queue alerts for processing on a Java-managed thread.
            sessionManager.addListener(object : AlertListener {
                override fun types() = null // Listen to all alert types

                override fun alert(alert: Alert<*>) {
                    // Simply queue the alert - don't process it here
                    // This prevents native thread crashes when accessing Java objects
                    alertQueue.offer(alert)
                }
            })

            sessionManager.start(SessionParams(settingsPack))

            // Log the listening status
            log.info("BitTorrent client started. Listen interfaces: ${settingsPack.listenInterfaces()}")

            return sessionManager
        }
    }

    private fun startAlertProcessor() {
        alertProcessorExecutor = Executors.newSingleThreadScheduledExecutor()
        alertProcessorExecutor?.scheduleWithFixedDelay({
            try {
                processQueuedAlerts()
            } catch (e: Exception) {
                log.error("Error processing queued alerts", e)
            }
        }, 0, 100, TimeUnit.MILLISECONDS) // Process alerts every 100ms
    }

    private fun processQueuedAlerts() {
        // Process all queued alerts on this Java-managed thread
        while (true) {
            val alert = alertQueue.poll() ?: break

            try {
                when {
                    alert.category().eq(Alert.ERROR_NOTIFICATION) ||
                            alert.type().name.contains("error", ignoreCase = true) -> {
                        log.debug("[libtorrent] {}: {}", alert.type(), alert.message())
                    }

                    else -> {
                        log.trace("[libtorrent] {}: {}", alert.type(), alert.message())
                    }
                }
            } catch (e: Exception) {
                // Log but don't rethrow - we don't want to stop processing other alerts
                log.error("Error processing libtorrent alert", e)
            }
        }
    }

    private fun startMonitoringTask() {
        monitorExecutor = Executors.newSingleThreadScheduledExecutor()
        monitorExecutor?.scheduleWithFixedDelay({
            try {
                checkAndStopCompletedTorrents()
            } catch (e: Exception) {
                log.error("Error checking torrent completion status", e)
            }
        }, 60, 60, TimeUnit.SECONDS) // Check every 60 seconds
    }

    private fun checkAndStopCompletedTorrents() {
        synchronized(SessionLock) {
            val handles = session?.torrentHandles ?: return

            handles.forEach { handle ->
                if (!handle.isValid) {
                    return@forEach
                }

                val status = handle.status(QUERY_DISTRIBUTED_COPIES.or_(QUERY_NAME))

                // Only check torrents that we are seeding
                if (status.isFinished) {
                    val knownSeeders = status.listSeeds()
                    val completePeersFromTracker = status.numComplete()
                    val distributedFullCopies = status.distributedFullCopies()

                    // If there are other seeders or complete peers, stop seeding
                    if (distributedFullCopies > 0) {
                        log.debug("Stopping seeding for torrent '${status.name()}' as it is healthy: $distributedFullCopies distributed full copies.")
                        session?.remove(handle)
                    } else if (completePeersFromTracker > 1) {
                        log.debug("Stopping seeding for torrent '${status.name()}' as it is healthy: $completePeersFromTracker complete peers from tracker.")
                        session?.remove(handle)
                    } else if (knownSeeders > 0) {
                        log.debug("Stopping seeding for torrent '${status.name()}' as it is healthy: $knownSeeders known seeders.")
                        session?.remove(handle)
                    } else {
                        log.debug("Continuing to seed torrent '${status.name()}' - no other complete peers found.")
                    }
                }
            }
        }
    }
}