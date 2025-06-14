package de.grimsi.gameyfinplugins.torrentdownload

import bt.torrent.maker.TorrentBuilder
import com.turn.ttorrent.client.CommunicationManager
import com.turn.ttorrent.client.SelectorFactoryImpl
import com.turn.ttorrent.client.storage.FullyPieceStorageFactory
import com.turn.ttorrent.network.FirstAvailableChannel
import com.turn.ttorrent.tracker.TrackedTorrent
import com.turn.ttorrent.tracker.Tracker
import de.grimsi.gameyfin.pluginapi.core.config.ConfigMetadata
import de.grimsi.gameyfin.pluginapi.core.config.PluginConfigMetadata
import de.grimsi.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import de.grimsi.gameyfin.pluginapi.core.wrapper.ConfigurableGameyfinPlugin
import de.grimsi.gameyfin.pluginapi.download.Download
import de.grimsi.gameyfin.pluginapi.download.DownloadProvider
import de.grimsi.gameyfin.pluginapi.download.FileDownload
import org.pf4j.Extension
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.*
import kotlin.time.measureTimedValue

class TorrentDownloadPlugin(wrapper: PluginWrapper) : ConfigurableGameyfinPlugin(wrapper) {

    companion object {
        private val TORRENT_FILE_DIRECTORY = Path.of("torrent_dotfiles")
        private lateinit var tracker: Tracker
        private lateinit var communicationManager: CommunicationManager

        private lateinit var plugin: TorrentDownloadPlugin
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

    @OptIn(ExperimentalPathApi::class)
    override fun start() {
        // Currently Gameyfin does not support storing plugin state
        // and since we can't associate the torrent files with a game path after a restart
        // we just delete the directory on startup.
        if (Files.exists(TORRENT_FILE_DIRECTORY)) {
            TORRENT_FILE_DIRECTORY.deleteRecursively()
        }
        Files.createDirectories(TORRENT_FILE_DIRECTORY)

        tracker = Tracker(config("trackerPort"), getTrackerUri().toString())
        tracker.setAcceptForeignTorrents(false)
        tracker.start(true)

        val workingExecutor = Executors.newVirtualThreadPerTaskExecutor()
        val validationExecutor = Executors.newVirtualThreadPerTaskExecutor()
        val clientPort = config<Int>("clientPort")
        communicationManager = CommunicationManager(workingExecutor, validationExecutor)
        communicationManager.start(
            arrayOf(getHostname()),
            15,
            getTrackerUri(),
            SelectorFactoryImpl(),
            FirstAvailableChannel(clientPort, clientPort)
        )
    }

    override fun stop() {
        tracker.stop()
        communicationManager.stop()
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

    private fun getTrackerUri(): URI {
        val protocol = "http" // No SSL support in ttorrent: https://github.com/mpetazzoni/ttorrent/issues/4
        val host = getHostname().getCanonicalHostName()
        val port = config<Int>("trackerPort")
        val path = "announce"

        return URI.create("$protocol://$host:$port/$path")
    }

    private fun getHostname(): InetAddress {
        return InetAddress.getByName(
            optionalConfig("externalHost") ?: InetAddress.getLocalHost().hostAddress
        )
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
            val torrentFile =
                TORRENT_FILE_DIRECTORY.resolve("${gameFilesPath.nameWithoutExtension}-${gameFilesPath.hashCode()}.torrent")

            if (Files.exists(torrentFile)) {
                return torrentFile
            }

            Files.createFile(torrentFile)
            Files.write(torrentFile, torrentFileContent(gameFilesPath))

            tracker.announce(TrackedTorrent.load(torrentFile.toFile()))
            communicationManager.addTorrent(
                torrentFile.toString(),
                getRootPath(gameFilesPath).toString(),
                FullyPieceStorageFactory.INSTANCE
            )

            return torrentFile
        }

        private fun torrentFileContent(gameFilesPath: Path): ByteArray {
            return TorrentBuilder()
                .numHashingThreads(Runtime.getRuntime().availableProcessors() * 2)
                .createdBy(plugin.javaClass.name)
                .addFile(gameFilesPath)
                .rootPath(getRootPath(gameFilesPath))
                .announce(plugin.getTrackerUri().toString())
                .privateFlag(plugin.config("privateMode"))
                .build()
        }

        private fun getRootPath(gameFilesPath: Path): Path {
            return if (gameFilesPath.isDirectory()) {
                gameFilesPath
            } else {
                gameFilesPath.parent
            }
        }
    }
}