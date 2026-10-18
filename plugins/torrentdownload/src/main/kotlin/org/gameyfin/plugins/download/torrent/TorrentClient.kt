package org.gameyfin.plugins.download.torrent

import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.TorrentHandle.QUERY_DISTRIBUTED_COPIES
import com.frostwire.jlibtorrent.TorrentHandle.QUERY_NAME
import com.frostwire.jlibtorrent.swig.libtorrent.*
import com.frostwire.jlibtorrent.swig.settings_pack.string_types
import com.frostwire.jlibtorrent.swig.torrent_flags_t
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
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
    private val performanceMode: TorrentClientPerformanceMode,
    private val dhtEnabled: Boolean,
    private val lsdEnabled: Boolean,
    private val stopSeedingWhenComplete: Boolean,
    private val diskIoMode: TorrentDiskIoMode = TorrentDiskIoMode.`Memory-mapped`
) {
    private val log = LoggerFactory.getLogger(TorrentClient::class.java)

    private var session: SessionManager? = null
    private var monitorExecutor: ScheduledExecutorService? = null

    companion object {
        private const val INTERNAL_PEER_ID_PREFIX = "-GF0001-"
    }

    fun start() {
        // Initialize session
        session = initSession()

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

        session?.stop()

        log.info("TorrentClient stopped")
    }

    /**
     * Adds a torrent for seeding.
     *
     * @param seedMode skip the full hash check and assume all pieces are present. Pieces are
     * then verified lazily when first requested by a peer. Only use this for torrents that were
     * created from the files on disk, otherwise a full check is needed.
     */
    fun addTorrent(torrentFile: Path, gameFile: Path, seedMode: Boolean = false) {
        val ti = TorrentInfo(torrentFile.toFile())

        // For seeding, we need to use the parent directory as the save path
        val savePath = gameFile.parent.toFile()

        // Check if torrent is already in session
        val existingHandle = session?.find(ti)
        if (existingHandle != null && existingHandle.isValid) {
            log.debug("Torrent ${ti.name()} is already in session, skipping")
            return
        }

        // Verify file access before adding to session
        if (!Files.isReadable(gameFile)) {
            log.error("Cannot read game file for seeding: $gameFile - check file permissions")
            error("Game file is not readable: $gameFile")
        }

        try {
            // Use SessionManager's download method - it will seed the files in the save directory.
            // The flags are OR'ed with libtorrent's default add_torrent_params flags.
            val flags = if (seedMode) TorrentFlags.SEED_MODE else torrent_flags_t()
            session?.download(ti, savePath, null, null, null, flags)
            log.info("Added torrent to session for seeding: ${ti.name()} from $savePath${if (seedMode) " (seed mode)" else ""}")
        } catch (e: Exception) {
            log.error("Failed to add torrent to session for seeding: ${ti.name()}", e)
            throw e
        }
    }

    /**
     * Removes the torrent from the session, so it is no longer seeded. The game files are not touched.
     *
     * @return whether the torrent has been part of the session
     */
    fun removeTorrent(torrentFile: Path): Boolean {
        val ti = TorrentInfo(torrentFile.toFile())
        val handle = session?.find(ti) ?: return false
        if (!handle.isValid) return false

        session?.remove(handle)
        log.info("Removed torrent from session: ${ti.name()}")
        return true
    }

    private fun initSession(): SessionManager {
        // This method is always called from the session executor thread
        // Return existing session if already initialized
        session?.let { return it }

        // Initialize jlibtorrent session
        val sessionManager = SessionManager()

        // Configure session settings based on performance mode
        val settingsPack = when (performanceMode) {
            TorrentClientPerformanceMode.Balanced -> SettingsPack(default_settings())
            TorrentClientPerformanceMode.`High Performance` -> SettingsPack(high_performance_seed())
            TorrentClientPerformanceMode.`Minimal Memory Usage` -> SettingsPack(min_memory_usage())
        }
        log.info("Configured TorrentClient with performance mode: $performanceMode")


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

        // Seed every torrent at once. libtorrent auto-manages torrents and by default keeps only
        // 5 seeds (and 15 torrents) active, queueing the rest, so most games would not be seeded.
        settingsPack.activeSeeds(-1)
        settingsPack.activeLimit(-1)

        // Configure DHT
        settingsPack.isEnableDht = dhtEnabled

        // Configure Local Peer Discovery
        settingsPack.isEnableLsd = lsdEnabled

        val sessionParams = SessionParams(settingsPack)

        // Configure disk I/O based on operating system
        // This must be done because libtorrent 2.0 uses memory mapped files which conflict with java runtime handlers
        // resulting in SIGSEGV crashes
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("win") -> {
                sessionParams.setDefaultDiskIO()
                log.info("Configured disk I/O for Windows (default disk I/O)")
            }

            (os.contains("nix") || os.contains("nux") || os.contains("mac") || os.contains("darwin"))
                    && diskIoMode == TorrentDiskIoMode.`Memory-mapped` -> {
                sessionParams.setDefaultDiskIO()
                log.info("Configured disk I/O for Unix-like system (memory-mapped disk I/O)")
                if (!isSignalChainingActive()) {
                    log.warn(
                        "Memory-mapped disk I/O without JVM signal chaining: libtorrent replaces the JVM's " +
                                "SIGSEGV handler and the JVM will crash. Set LD_PRELOAD to libjsig.so " +
                                "(e.g. /opt/java/openjdk/lib/libjsig.so) or use POSIX disk I/O."
                    )
                }
            }

            os.contains("nix") || os.contains("nux") || os.contains("mac") || os.contains("darwin") -> {
                sessionParams.setPosixDiskIO()
                log.info("Configured disk I/O for Unix-like system (POSIX disk I/O)")
            }

            else -> {
                log.info("Unknown OS '$os', using default disk I/O settings")
            }
        }

        sessionManager.start(sessionParams)

        // Log the listening status
        log.info("BitTorrent client started. Listen interfaces: ${settingsPack.listenInterfaces()}")

        return sessionManager
    }

    /** True if HotSpot's signal-chaining library (libjsig) is loaded into this process. */
    private fun isSignalChainingActive(): Boolean = try {
        Files.readAllLines(Path.of("/proc/self/maps")).any { it.contains("libjsig") }
    } catch (_: Exception) {
        System.getenv("LD_PRELOAD")?.contains("libjsig") == true
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
                when {
                    distributedFullCopies > 0 -> {
                        log.debug("Stopping seeding for torrent '${status.name()}' as it is healthy: $distributedFullCopies distributed full copies.")
                        session?.remove(handle)
                    }

                    completePeersFromTracker > 1 -> {
                        log.debug("Stopping seeding for torrent '${status.name()}' as it is healthy: $completePeersFromTracker complete peers from tracker.")
                        session?.remove(handle)
                    }

                    knownSeeders > 0 -> {
                        log.debug("Stopping seeding for torrent '${status.name()}' as it is healthy: $knownSeeders known seeders.")
                        session?.remove(handle)
                    }

                    else -> {
                        log.debug("Continuing to seed torrent '${status.name()}' - no other complete peers found.")
                    }
                }
            }
        }
    }
}