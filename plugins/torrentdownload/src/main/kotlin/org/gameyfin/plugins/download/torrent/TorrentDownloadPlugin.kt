package org.gameyfin.plugins.download.torrent

import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.gameyfin.pluginapi.core.config.PluginConfigMetadata
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import org.gameyfin.pluginapi.core.wrapper.ConfigurableGameyfinPlugin
import org.gameyfin.pluginapi.download.Download
import org.gameyfin.pluginapi.download.DownloadProvider
import org.gameyfin.pluginapi.download.FileDownload
import org.libtorrent4j.*
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
        private lateinit var sessionManager: SessionManager
        private lateinit var trackerServer: LibtorrentTracker

        private lateinit var plugin: TorrentDownloadPlugin
        private lateinit var state: TorrentDownloadPluginState

        private val logger = LoggerFactory.getLogger(TorrentDownloadPlugin::class.java)

        init {
            // Load native library before any libtorrent4j classes are used
            NativeLibraryLoader.loadLibtorrent4j()
        }
    }

    init {
        plugin = this
    }

    override val configMetadata: PluginConfigMetadata = listOf(
        ConfigMetadata(
            key = "trackerPort",
            label = "Tracker Port",
            description = "Which port the torrent tracker should use",
            type = Int::class.java,
            default = 6969
        ),
        ConfigMetadata(
            key = "clientPort",
            label = "Seed Client Port",
            description = "Which port the seed client should use",
            type = Int::class.java,
            default = 6881
        ),
        ConfigMetadata(
            key = "externalHost",
            label = "Hostname/IP override",
            description = "Overrides the external host (e.g., if behind NAT)",
            type = String::class.java,
            isRequired = false
        ),
        ConfigMetadata(
            key = "privateMode",
            label = "Create torrents with private mode enabled",
            description = "Enables private mode for the torrent tracker according to BEP-27",
            type = Boolean::class.java,
            default = true
        )
    )

    override fun start() {
        Files.createDirectories(dataDirectory)

        // Initialize libtorrent session
        val settingsPack = SettingsPack()
        val listenInterfaces = "0.0.0.0:${config<Int>("clientPort")}"
        settingsPack.listenInterfaces(listenInterfaces)

        // Enable DHT for peer discovery
        settingsPack.setEnableDht(true)

        // Set announce IP to external host if specified
        val externalHost = optionalConfig<String>("externalHost")
        if (externalHost != null) {
            settingsPack.setString(
                org.libtorrent4j.swig.settings_pack.string_types.announce_ip.swigValue(),
                externalHost
            )
        }

        // Optimize for seeding
        settingsPack.uploadRateLimit(0) // Unlimited
        settingsPack.connectionsLimit(200)
        settingsPack.activeSeeds(-1) // Unlimited active seeds

        val sessionParams = SessionParams(settingsPack)
        sessionManager = SessionManager(true)
        sessionManager.start(sessionParams)

        // Start the tracker
        trackerServer = LibtorrentTracker(
            port = config("trackerPort"),
            externalHost = getExternalHost()
        )
        trackerServer.start()

        // Load state and resume torrents
        state = loadState<TorrentDownloadPluginState>() ?: TorrentDownloadPluginState()

        state.torrentFilesMetadata.forEach {
            // Check if the torrent and game files exist and
            // that the game files have not been modified since the torrent file was created
            if (Files.exists(it.torrentFile) && Files.exists(it.gameFile) &&
                it.gameFile.getLastModifiedTime().toInstant().isBefore(it.torrentFile.getLastModifiedTime().toInstant())
            ) {
                try {
                    addTorrentToSession(it.torrentFile, it.gameFile)
                    trackerServer.addTorrent(it.torrentFile)
                } catch (e: Exception) {
                    logger.error("Failed to resume torrent for ${it.gameFile}", e)
                    state.torrentFilesMetadata.remove(it)
                }
            } else {
                state.torrentFilesMetadata.remove(it)
            }
        }

        saveState(state)
    }

    override fun stop() {
        trackerServer.stop()
        sessionManager.stop()
    }

    override fun validateConfig(config: Map<String, String?>): PluginConfigValidationResult {
        val configValidationResult = super.validateConfig(config)
        if (!configValidationResult.isValid()) {
            return configValidationResult
        }

        val errors = mutableMapOf<String, String>()

        val trackerPort = config["trackerPort"]?.toIntOrNull()
        if (trackerPort != null && trackerPort !in 1024..49151) {
            errors["trackerPort"] = "Must be a valid port number between 1024 and 49151."
        }

        val clientPort = config["clientPort"]?.toIntOrNull()
        if (clientPort != null && clientPort !in 1024..49151) {
            errors["clientPort"] = "Must be a valid port number between 1024 and 49151."
        }

        val externalHost = config["externalHost"]
        if (externalHost != null) {
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

    private fun getTrackerUrl(): String {
        val protocol = "http"
        val host = getExternalHost()
        val port = config<Int>("trackerPort")
        return "$protocol://$host:$port/announce"
    }

    private fun getExternalHost(): String {
        return optionalConfig("externalHost") ?: InetAddress.getLocalHost().hostAddress
    }

    private fun getRootPath(gameFilesPath: Path): Path {
        return if (gameFilesPath.isDirectory()) {
            gameFilesPath
        } else {
            gameFilesPath.parent
        }
    }

    private fun addTorrentToSession(torrentFile: Path, gameFilePath: Path) {
        val ti = TorrentInfo(torrentFile.toFile())
        val savePath = getRootPath(gameFilePath).toFile()

        sessionManager.download(ti, savePath)
        logger.info("Added torrent to session: ${torrentFile.name}")
    }

    @Extension(ordinal = 2)
    class TorrentDownloadProvider : DownloadProvider {
        private val logger = LoggerFactory.getLogger(TorrentDownloadProvider::class.java)

        override fun download(path: Path): Download {
            logger.info("Creating torrent for '${path.name}'...")

            val (torrentFile, timeTaken) = measureTimedValue {
                createTorrent(path)
            }

            logger.info("Created torrent '${torrentFile.name}' in ${timeTaken.asHumanReadable()}")

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

            // Create torrent file using TorrentBuilder
            val builder = TorrentBuilder()

            // Configure the builder
            builder.comment("Generated by Gameyfin TorrentDownloadPlugin")
            builder.creator("Gameyfin/${plugin.javaClass.`package`.implementationVersion ?: "dev"}")
            builder.setPrivate(plugin.config("privateMode"))
            builder.addTracker(plugin.getTrackerUrl())

            // Set the path to create torrent from
            builder.path(gameFilesPath.toFile())

            // Build the torrent synchronously
            val result = builder.generate()

            // Save the torrent file
            val entry = result.entry()
            val torrentData = entry.bencode()
            Files.write(torrentFile, torrentData)

            // Add to tracker and session
            plugin.addTorrentToSession(torrentFile, gameFilesPath)
            trackerServer.addTorrent(torrentFile)

            state.torrentFilesMetadata.add(
                TorrentFileMetadata(
                    torrentFile = torrentFile,
                    gameFile = gameFilesPath
                )
            )

            plugin.saveState(state)

            return torrentFile
        }
    }
}
