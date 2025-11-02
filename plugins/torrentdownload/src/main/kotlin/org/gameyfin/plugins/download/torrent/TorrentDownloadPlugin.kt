package org.gameyfin.plugins.download.torrent

import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.TorrentHandle.QUERY_DISTRIBUTED_COPIES
import com.frostwire.jlibtorrent.TorrentHandle.QUERY_NAME
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.swig.create_torrent
import com.frostwire.jlibtorrent.swig.error_code
import com.frostwire.jlibtorrent.swig.file_storage
import com.frostwire.jlibtorrent.swig.libtorrent.add_files
import com.frostwire.jlibtorrent.swig.libtorrent.set_piece_hashes_ex
import com.frostwire.jlibtorrent.swig.set_piece_hashes_listener
import com.frostwire.jlibtorrent.swig.settings_pack.string_types
import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.gameyfin.pluginapi.core.config.PluginConfigMetadata
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import org.gameyfin.pluginapi.core.wrapper.ConfigurableGameyfinPlugin
import org.gameyfin.pluginapi.download.Download
import org.gameyfin.pluginapi.download.DownloadProvider
import org.gameyfin.pluginapi.download.FileDownload
import org.pf4j.Extension
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.io.path.*
import kotlin.time.measureTimedValue

class TorrentDownloadPlugin(wrapper: PluginWrapper) : ConfigurableGameyfinPlugin(wrapper) {

    companion object {
        private const val INTERNAL_PEER_ID_PREFIX = "-GF0001-"

        private lateinit var plugin: TorrentDownloadPlugin
        private lateinit var state: TorrentDownloadPluginState

        private var session: SessionManager? = null
        private var tracker: TorrentTracker? = null
        private var monitorExecutor: ScheduledExecutorService? = null
    }

    init {
        plugin = this
    }

    override val configMetadata: PluginConfigMetadata = listOf(
        ConfigMetadata(
            key = "stopSeedingWhenComplete",
            label = "Stop Seeding When Complete",
            description = "Automatically stop seeding torrents once there are other peers with all pieces (torrent is healthy)",
            type = Boolean::class.java,
            default = false
        ),
        ConfigMetadata(
            key = "privateMode",
            label = "Create torrents with private mode enabled",
            description = "Enables private mode for torrents according to BEP-27",
            type = Boolean::class.java,
            default = true
        ),
        ConfigMetadata(
            key = "dhtEnabled",
            label = "Enable DHT",
            description = "Enable Distributed Hash Table for peer discovery",
            type = Boolean::class.java,
            default = false
        ),
        ConfigMetadata(
            key = "lsdEnabled",
            label = "Enable LSD",
            description = "Enable Local Service Discovery for finding peers on the local network",
            type = Boolean::class.java,
            default = false
        ),
        ConfigMetadata(
            key = "externalHost",
            label = "Hostname/IP override",
            description = "Overrides the external host for the built-in tracker (e.g., if behind NAT/Docker)",
            type = String::class.java,
            isRequired = false
        ),
        ConfigMetadata(
            key = "listenPort",
            label = "Listen Port",
            description = "Which port the built-in torrent client should listen on",
            type = Int::class.java,
            default = 6881
        ),
        ConfigMetadata(
            key = "trackerPort",
            label = "Tracker Port",
            description = "Which port the built-in tracker should listen on",
            type = Int::class.java,
            default = 6969
        ),
        ConfigMetadata(
            key = "announceInterval",
            label = "Tracker Announce Interval (in seconds)",
            description = "Interval for clients to re-announce to the tracker",
            type = Int::class.java,
            default = 1800
        )
    )

    override fun start() {
        Files.createDirectories(dataDirectory)

        session = initSession()

        tracker = initTracker()

        state = loadState<TorrentDownloadPluginState>() ?: TorrentDownloadPluginState()

        // Restore existing torrents and remove invalid ones
        state.torrentFilesMetadata.removeIf { metadata ->
            val shouldRemove = !Files.exists(metadata.torrentFile) ||
                    !Files.exists(metadata.gameFile) ||
                    metadata.gameFile.getLastModifiedTime().toInstant()
                        .isAfter(metadata.torrentFile.getLastModifiedTime().toInstant())

            if (shouldRemove) {
                true
            } else {
                try {
                    addTorrentToSession(metadata.torrentFile, metadata.gameFile)
                    false
                } catch (e: Exception) {
                    log.error("Failed to add torrent ${metadata.torrentFile} to session", e)
                    true
                }
            }
        }

        saveState(state)

        // Start monitoring task if stopSeedingWhenComplete is enabled
        if (config("stopSeedingWhenComplete")) {
            startMonitoringTask()
        }
    }

    private fun initSession(): SessionManager {
        // Initialize jlibtorrent session
        val sessionManager = SessionManager()

        // Configure session settings with custom peer ID
        val settingsPack = sessionManager.settings() ?: SettingsPack()


        // Set custom peer ID prefix for our internal client
        // This allows us to identify this specific client if needed
        settingsPack.peerFingerprint = INTERNAL_PEER_ID_PREFIX.toByteArray()

        // Configure interfaces
        val listenPort = config<Int>("listenPort")
        settingsPack.listenInterfaces("0.0.0.0:$listenPort,[::]:$listenPort")

        // Configure announce IP if externalHost is set
        val externalHost = optionalConfig<String>("externalHost")
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
        val dhtEnabled = config<Boolean>("dhtEnabled")
        settingsPack.isEnableDht = dhtEnabled

        // Configure Local Peer Discovery
        val lpdEnabled = config<Boolean>("lsdEnabled")
        settingsPack.isEnableLsd = lpdEnabled

        // Add alert listener to log errors and connection attempts
        sessionManager.addListener(object : AlertListener {
            override fun types() = null // Listen to all alert types

            override fun alert(alert: Alert<*>) {
                when {
                    alert.category().eq(Alert.ERROR_NOTIFICATION) ||
                            alert.type().name.contains("error", ignoreCase = true) -> {
                        log.debug("[libtorrent] {}: {}", alert.type(), alert.message())
                    }

                    else -> {
                        log.trace("[libtorrent] {}: {}", alert.type(), alert.message())
                    }
                }
            }
        })

        sessionManager.start(SessionParams(settingsPack))

        // Log the listening status
        log.info("BitTorrent client started. Listen interfaces: ${settingsPack.listenInterfaces()}")

        return sessionManager
    }

    private fun initTracker(): TorrentTracker {
        // Start built-in tracker with the peer ID prefix to identify internal client
        val trackerPort = config<Int>("trackerPort")
        val announceInterval = config<Int>("announceInterval")

        val tracker = TorrentTracker(
            port = trackerPort,
            announceInterval = announceInterval
        )

        tracker.start()

        return tracker
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
        val handles = session?.torrentHandles?.toList() ?: return

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

    private fun addTorrentToSession(torrentFile: Path, gameFile: Path) {
        val ti = TorrentInfo(torrentFile.toFile())

        // For seeding, we need to use the parent directory as the save path
        // This matches how we created the torrent with hashBasePath = parent directory
        val savePath = if (gameFile.isDirectory()) {
            gameFile.parent.toFile()
        } else {
            gameFile.parent.toFile()
        }

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

    override fun stop() {

        monitorExecutor?.shutdown()
        try {
            monitorExecutor?.awaitTermination(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            monitorExecutor?.shutdownNow()
        }
        monitorExecutor = null

        session?.stop()
        session = null

        tracker?.stop()
        tracker = null
    }

    override fun validateConfig(config: Map<String, String?>): PluginConfigValidationResult {
        val configValidationResult = super.validateConfig(config)
        if (!configValidationResult.isValid()) {
            return configValidationResult
        }

        val errors = mutableMapOf<String, String>()

        val listenPort = config["listenPort"]?.toIntOrNull()
        if (listenPort != null && listenPort !in 1024..65535) {
            errors["listenPort"] = "Must be a valid port number between 1024 and 65535."
        }

        val trackerPort = config["trackerPort"]?.toIntOrNull()
        if (trackerPort != null && trackerPort !in 1024..65535) {
            errors["trackerPort"] = "Must be a valid port number between 1024 and 65535."
        }

        val externalHost = config["externalHost"]
        if (!externalHost.isNullOrBlank()) {
            try {
                InetAddress.getByName(externalHost)
            } catch (_: Exception) {
                errors["externalHost"] = "Must be a valid hostname or IP address."
            }
        } else if (System.getenv("RUNTIME_ENV") == "docker") {
            errors["externalHost"] = "Must be set when running in Docker."
        }

        val announceInterval = config["announceInterval"]?.toIntOrNull()
        if (announceInterval != null && announceInterval <= 0) {
            errors["announceInterval"] = "Must be a positive integer."
        }

        return if (errors.isEmpty()) {
            PluginConfigValidationResult.VALID
        } else {
            PluginConfigValidationResult.INVALID(errors)
        }
    }

    private fun getTrackerUri(): URI {
        val protocol = "http"
        val host = optionalConfig("externalHost") ?: InetAddress.getLocalHost().hostAddress
        val port = config<Int>("trackerPort")
        val path = "announce"

        return URI.create("$protocol://$host:$port/$path")
    }


    @Extension(ordinal = 2)
    class TorrentDownloadProvider : DownloadProvider {
        private val log = LoggerFactory.getLogger(TorrentDownloadProvider::class.java)

        override fun download(path: Path): Download {
            log.info("Creating torrent for '${path.name}'...")

            val (torrentFile, timeTaken) = measureTimedValue {
                createTorrent(path)
            }

            log.info("Created torrent '${torrentFile.name}' in ${timeTaken.asHumanReadable()}")

            return FileDownload(
                data = torrentFile.inputStream(),
                fileExtension = "torrent",
                size = torrentFile.fileSize()
            )
        }

        private fun createTorrent(gameFilesPath: Path): Path {
            val torrentFile = plugin.dataDirectory
                .resolve("${gameFilesPath.nameWithoutExtension}-${gameFilesPath.hashCode()}.torrent")

            val isNewTorrent = !Files.exists(torrentFile)

            if (isNewTorrent) {
                Files.createFile(torrentFile)
                Files.write(torrentFile, torrentFileContent(gameFilesPath))

                state.torrentFilesMetadata.add(
                    TorrentFileMetadata(
                        torrentFile = torrentFile,
                        gameFile = gameFilesPath
                    )
                )

                plugin.saveState(state)
            }

            // Add the torrent to the session for seeding asynchronously to avoid blocking the download
            // This prevents crashes if there are permission issues or other errors
            try {
                plugin.addTorrentToSession(torrentFile, gameFilesPath)
            } catch (e: Exception) {
                log.error("Failed to add torrent to seeding session - torrent file created but won't be seeded", e)
                // Don't rethrow - the torrent file was created successfully, seeding is optional
            }

            return torrentFile
        }

        private fun torrentFileContent(gameFilesPath: Path): ByteArray {
            val isDirectory = gameFilesPath.isDirectory()

            // Create file storage
            val fs = file_storage()

            // For directories, we need to add files from the directory and set the name
            // For single files, we add just the file
            val hashBasePath: String
            if (isDirectory) {
                // Add all files from the directory
                add_files(fs, gameFilesPath.toString())
                // Set the name to just the directory name (not full path)
                fs.set_name(gameFilesPath.fileName.toString())
                // For hashing, use parent directory (because torrent name is set to directory name)
                hashBasePath = gameFilesPath.parent.toString()
            } else {
                // For single files, add just that file
                add_files(fs, gameFilesPath.toString())
                // For hashing, use the parent directory
                hashBasePath = gameFilesPath.parent.toString()
            }

            // Create torrent
            val ct = create_torrent(fs)

            // Add tracker announce URL (use built-in tracker if not overridden)
            val announceUrl = plugin.getTrackerUri().toString()
            ct.add_tracker(announceUrl)

            log.info("Creating torrent with announce URL: $announceUrl")

            // Set private flag if configured
            if (plugin.config("privateMode")) {
                ct.set_priv(true)
            }

            // Set creator
            ct.set_creator("gameyfin-torrent-plugin")

            // Generate piece hashes
            val ec = error_code()
            val listener = set_piece_hashes_listener()
            set_piece_hashes_ex(ct, hashBasePath, listener, ec)

            if (ec.value() != 0) {
                throw RuntimeException("Failed to set piece hashes: ${ec.message()}")
            }

            // Generate the torrent bencode
            val entry = ct.generate()
            return Vectors.byte_vector2bytes(entry.bencode())
        }
    }
}