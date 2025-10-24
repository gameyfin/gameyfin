package org.gameyfin.plugins.download.torrent

import bt.torrent.maker.TorrentBuilder
import com.turn.ttorrent.client.CommunicationManager
import com.turn.ttorrent.client.SelectorFactoryImpl
import com.turn.ttorrent.client.storage.FullyPieceStorageFactory
import com.turn.ttorrent.network.FirstAvailableChannel
import com.turn.ttorrent.tracker.TrackedTorrent
import com.turn.ttorrent.tracker.Tracker
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
import kotlin.io.path.*
import kotlin.time.measureTimedValue

class TorrentDownloadPlugin(wrapper: PluginWrapper) : ConfigurableGameyfinPlugin(wrapper) {

    companion object {
        private lateinit var tracker: Tracker
        private lateinit var communicationManager: CommunicationManager

        private lateinit var plugin: TorrentDownloadPlugin

        private lateinit var state: TorrentDownloadPluginState
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

        state = loadState<TorrentDownloadPluginState>() ?: TorrentDownloadPluginState()

        state.torrentFilesMetadata.forEach {
            // Check if the torrent and game files exist and
            // that the game files have not been modified since the torrent file was created
            if (Files.exists(it.torrentFile) && Files.exists(it.gameFile) &&
                it.gameFile.getLastModifiedTime().toInstant().isBefore(it.torrentFile.getLastModifiedTime().toInstant())
            ) {
                tracker.announce(TrackedTorrent.load(it.torrentFile.toFile()))
                communicationManager.addTorrent(
                    it.torrentFile.toString(),
                    getRootPath(it.gameFile).toString(),
                    FullyPieceStorageFactory.INSTANCE
                )
            } else {
                state.torrentFilesMetadata.remove(it)
            }
        }

        saveState(state)
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
        val host = getHostname().getHostName()
        val port = config<Int>("trackerPort")
        val path = "announce"

        return URI.create("$protocol://$host:$port/$path")
    }

    private fun getHostname(): InetAddress {
        return InetAddress.getByName(
            optionalConfig("externalHost") ?: InetAddress.getLocalHost().hostAddress
        )
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

            tracker.announce(TrackedTorrent.load(torrentFile.toFile()))
            communicationManager.addTorrent(
                torrentFile.toString(),
                plugin.getRootPath(gameFilesPath).toString(),
                FullyPieceStorageFactory.INSTANCE
            )

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
            return TorrentBuilder()
                .numHashingThreads(Runtime.getRuntime().availableProcessors() * 2)
                .createdBy(plugin.javaClass.name)
                .addFile(gameFilesPath)
                .rootPath(plugin.getRootPath(gameFilesPath))
                .announce(plugin.getTrackerUri().toString())
                .privateFlag(plugin.config("privateMode"))
                .build()
        }
    }
}