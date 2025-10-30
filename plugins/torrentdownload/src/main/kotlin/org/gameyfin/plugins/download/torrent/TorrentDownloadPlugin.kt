package org.gameyfin.plugins.download.torrent

import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.SettingsPack
import com.frostwire.jlibtorrent.TorrentInfo
import com.frostwire.jlibtorrent.Vectors
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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.time.measureTimedValue

class TorrentDownloadPlugin(wrapper: PluginWrapper) : ConfigurableGameyfinPlugin(wrapper) {

    companion object {
        private var session: SessionManager? = null
        private var tracker: TorrentTracker? = null
        private lateinit var plugin: TorrentDownloadPlugin

        private lateinit var state: TorrentDownloadPluginState
    }

    init {
        plugin = this
    }

    private val log = LoggerFactory.getLogger(TorrentDownloadPlugin::class.java)

    override val configMetadata: PluginConfigMetadata = listOf(
        ConfigMetadata(
            key = "listenPort",
            label = "Listen Port",
            description = "Which port the torrent client should listen on",
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
            key = "externalHost",
            label = "Hostname/IP override",
            description = "Overrides the external host (e.g., if behind NAT)",
            type = String::class.java,
            isRequired = false
        ),
        ConfigMetadata(
            key = "announceUrl",
            label = "Tracker Announce URL (Optional)",
            description = "Override the tracker announce URL (by default uses built-in tracker)",
            type = String::class.java,
            isRequired = false
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
        )
    )

    override fun start() {
        Files.createDirectories(dataDirectory)

        // Start built-in tracker
        val trackerPort = config<Int>("trackerPort")
        tracker = TorrentTracker(port = trackerPort)
        tracker?.start()

        // Initialize jlibtorrent session
        session = SessionManager()

        // Configure session settings
        val settingsPack = SettingsPack()
        val listenPort = config<Int>("listenPort")
        settingsPack.listenInterfaces("0.0.0.0:$listenPort")

        // Configure DHT
        val dhtEnabled = config<Boolean>("dhtEnabled")
        settingsPack.swig()
            .set_bool(com.frostwire.jlibtorrent.swig.settings_pack.bool_types.enable_dht.swigValue(), dhtEnabled)

        session?.applySettings(settingsPack)
        session?.start()

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
    }

    private fun addTorrentToSession(torrentFile: Path, gameFile: Path) {
        val ti = TorrentInfo(torrentFile.toFile())

        // Use SessionManager's download method - it will seed the files in the save directory
        session?.download(ti, getRootPath(gameFile).toFile())
    }

    override fun stop() {
        // Stop in a dedicated shutdown thread
        val shutdownThread = Thread {
            session?.stop()
            session = null
            tracker?.stop()
            tracker = null
        }
        shutdownThread.start()
        shutdownThread.join()
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
        if (externalHost != null && externalHost.isNotBlank()) {
            try {
                InetAddress.getByName(externalHost)
            } catch (_: Exception) {
                errors["externalHost"] = "Must be a valid hostname or IP address."
            }
        }

        return if (errors.isEmpty()) {
            PluginConfigValidationResult.VALID
        } else {
            PluginConfigValidationResult.INVALID(errors)
        }
    }


    private fun getHostname(): InetAddress {
        return InetAddress.getByName(
            optionalConfig("externalHost") ?: InetAddress.getLocalHost().hostAddress
        )
    }

    private fun getAnnounceUrl(): String {
        // Use custom announce URL if configured, otherwise use built-in tracker
        return optionalConfig<String>("announceUrl") ?: tracker?.getAnnounceUrl()
        ?: "http://localhost:${config<Int>("trackerPort")}/announce"
    }

    private fun getRootPath(gameFilesPath: Path): Path {
        return if (gameFilesPath.isDirectory()) {
            gameFilesPath
        } else {
            gameFilesPath.parent
        }
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

            if (Files.exists(torrentFile)) {
                return torrentFile
            }

            Files.createFile(torrentFile)
            Files.write(torrentFile, torrentFileContent(gameFilesPath))

            // Add the torrent to the session for seeding
            plugin.addTorrentToSession(torrentFile, gameFilesPath)

            state.torrentFilesMetadata.add(
                TorrentFileMetadata(
                    torrentFile = torrentFile,
                    gameFile = gameFilesPath
                )
            )

            plugin.saveState(state)

            return torrentFile
        }

        private fun torrentFileContent(gameFilesPath: Path): ByteArray {
            val rootPath = plugin.getRootPath(gameFilesPath)

            // Create file storage
            val fs = com.frostwire.jlibtorrent.swig.file_storage()

            // Add files relative to root path
            if (gameFilesPath.isDirectory()) {
                com.frostwire.jlibtorrent.swig.libtorrent.add_files(fs, gameFilesPath.toString())
            } else {
                // For single files, use add_files on the parent directory
                // but we need to handle this properly
                com.frostwire.jlibtorrent.swig.libtorrent.add_files(fs, gameFilesPath.toString())
            }

            // Create torrent
            val ct = com.frostwire.jlibtorrent.swig.create_torrent(fs)

            // Add tracker announce URL (use built-in tracker if not overridden)
            val announceUrl = plugin.getAnnounceUrl()
            ct.add_tracker(announceUrl)

            log.info("Creating torrent with announce URL: $announceUrl")

            // Set private flag if configured
            if (plugin.config("privateMode")) {
                ct.set_priv(true)
            }

            // Set creator
            ct.set_creator("gameyfin-torrent-plugin")

            // Generate piece hashes
            val ec = com.frostwire.jlibtorrent.swig.error_code()
            val listener = com.frostwire.jlibtorrent.swig.set_piece_hashes_listener()
            com.frostwire.jlibtorrent.swig.libtorrent.set_piece_hashes_ex(ct, rootPath.toString(), listener, ec)

            if (ec.value() != 0) {
                throw RuntimeException("Failed to set piece hashes: ${ec.message()}")
            }

            // Generate the torrent bencode
            val entry = ct.generate()
            return Vectors.byte_vector2bytes(entry.bencode())
        }
    }
}