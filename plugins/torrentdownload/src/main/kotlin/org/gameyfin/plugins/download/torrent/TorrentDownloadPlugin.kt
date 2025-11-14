package org.gameyfin.plugins.download.torrent

import com.frostwire.jlibtorrent.TorrentBuilder
import com.frostwire.jlibtorrent.swig.create_torrent.v1_only
import com.frostwire.jlibtorrent.swig.create_torrent.v2_only
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
import kotlin.io.path.*
import kotlin.time.measureTimedValue

class TorrentDownloadPlugin(wrapper: PluginWrapper) : ConfigurableGameyfinPlugin(wrapper) {

    companion object {
        private lateinit var plugin: TorrentDownloadPlugin
        private lateinit var state: TorrentDownloadPluginState

        private var client: TorrentClient? = null
        private var tracker: TorrentTracker? = null
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
            key = "torrentVersions",
            label = "Torrent Protocol Versions",
            description = "Which torrent protocol versions to support (some clients don't support v2)",
            type = TorrentVersion::class.java,
            default = TorrentVersion.`V1 and V2`
        ),
        ConfigMetadata(
            key = "performanceMode",
            label = "Torrent Client Performance Mode",
            description = "Optimizes the torrent client for either low resource usage or high performance",
            type = TorrentClientPerformanceMode::class.java,
            default = TorrentClientPerformanceMode.Balanced
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

        tracker = initTracker()

        client = initClient()

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
                    client?.addTorrent(metadata.torrentFile, metadata.gameFile)
                    false
                } catch (e: Exception) {
                    log.error("Failed to add torrent ${metadata.torrentFile} to session", e)
                    true
                }
            }
        }

        saveState(state)
    }

    private fun initClient(): TorrentClient {
        val client = TorrentClient(
            listenPort = config("listenPort"),
            externalHost = optionalConfig("externalHost"),
            performanceMode = config("performanceMode"),
            dhtEnabled = config("dhtEnabled"),
            lsdEnabled = config("lsdEnabled"),
            stopSeedingWhenComplete = config("stopSeedingWhenComplete")
        )

        client.start()

        return client
    }

    private fun initTracker(): TorrentTracker {
        val tracker = TorrentTracker(
            port = config("trackerPort"),
            announceInterval = config("announceInterval")
        )

        tracker.start()

        return tracker
    }

    override fun stop() {
        client?.stop()
        client = null

        tracker?.stop()
        tracker = null
    }

    override fun validateConfig(config: Map<String, String?>): PluginConfigValidationResult {

        val errors = mutableMapOf<String, String>()

        // Plugin is not compatible with Alpine Docker images due to missing glibc
        if (System.getenv("RUNTIME_ENV") == "docker") {
            if (getContainerOS() == "alpine") {
                errors["stopSeedingWhenComplete"] = " "
                errors["privateMode"] = " "
                errors["dhtEnabled"] = " "
                errors["lsdEnabled"] = " "
                errors["torrentVersions"] = " "
                errors["performanceMode"] = " "
                errors["externalHost"] = " "
                errors["listenPort"] = " "
                errors["trackerPort"] = " "
                errors["announceInterval"] =
                    "The torrent plugin is not compatible with the Alpine-based Docker image. Please use the Ubuntu-based Docker image if you want to use the Torrent plugin."
                return PluginConfigValidationResult.INVALID(errors)
            }
        }

        val configValidationResult = super.validateConfig(config)
        if (!configValidationResult.isValid()) {
            return configValidationResult
        }

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
                initNewTorrent(path)
            }

            log.info("Created torrent '${torrentFile.name}' in ${timeTaken.asHumanReadable()}")

            return FileDownload(
                data = torrentFile.inputStream(),
                fileExtension = "torrent",
                size = torrentFile.fileSize()
            )
        }

        private fun initNewTorrent(gameFilesPath: Path): Path {
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
                client?.addTorrent(torrentFile, gameFilesPath)
            } catch (e: Exception) {
                log.error("Failed to add torrent to seeding session - torrent file created but won't be seeded", e)
                // Don't rethrow - the torrent file was created successfully, seeding is optional
            }

            return torrentFile
        }

        @Suppress("DEPRECATION")
        private fun torrentFileContent(gameFilesPath: Path): ByteArray {
            val torrentBuilder = TorrentBuilder()

            val trackerUrl = plugin.getTrackerUri().toString()
            val isPrivate = plugin.config<Boolean>("privateMode")
            val torrentVersions = plugin.config<TorrentVersion>("torrentVersions")

            log.info("Creating ${if (isPrivate) "private" else "public"} ${if (torrentVersions !== TorrentVersion.`V1 and V2`) torrentVersions else ""} torrent with announce URL '$trackerUrl'")

            val flags = when (torrentVersions) {
                TorrentVersion.`V1 only` -> v1_only
                TorrentVersion.`V2 only` -> v2_only
                TorrentVersion.`V1 and V2` -> null
            }

            val builder = torrentBuilder.path(gameFilesPath.toFile())
                .creator("Gameyfin Torrent plugin v${plugin.wrapper.descriptor.version}")
                .addTracker(trackerUrl)
                .setPrivate(isPrivate)

            if (flags != null) {
                builder.flags(flags)
            }

            val builderResult = builder.generate()

            return builderResult.entry().bencode()
        }
    }
}