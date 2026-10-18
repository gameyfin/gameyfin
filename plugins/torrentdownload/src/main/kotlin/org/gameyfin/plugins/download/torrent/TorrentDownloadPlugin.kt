package org.gameyfin.plugins.download.torrent

import com.frostwire.jlibtorrent.TorrentBuilder
import com.frostwire.jlibtorrent.TorrentInfo
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
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*
import kotlin.time.measureTimedValue

class TorrentDownloadPlugin(wrapper: PluginWrapper) : ConfigurableGameyfinPlugin(wrapper) {

    companion object {
        internal lateinit var plugin: TorrentDownloadPlugin
        internal lateinit var state: TorrentDownloadPluginState

        internal var client: TorrentClient? = null
        private var tracker: TorrentTracker? = null

        private val creationLocks = ConcurrentHashMap<Path, Any>()

        /**
         * Lock for all operations on a torrent file: concurrent requests wait for a running creation
         * instead of receiving a half-written (empty) torrent file.
         */
        internal fun lockFor(torrentFile: Path): Any = creationLocks.computeIfAbsent(torrentFile) { Any() }

        /**
         * A cached .torrent file can be reused if it is non-empty, parses, and (if given) announces
         * to the currently configured tracker. Interrupted creations leave empty files, and changing
         * the tracker host/port would otherwise keep serving torrents with the old announce URL.
         */
        internal fun isUsableTorrentFile(torrentFile: Path, expectedTrackerUrl: String? = null): Boolean {
            if (!Files.exists(torrentFile) || Files.size(torrentFile) == 0L) return false
            return try {
                val info = TorrentInfo(torrentFile.toFile())
                expectedTrackerUrl == null || info.trackers().any { it.url() == expectedTrackerUrl }
            } catch (_: Exception) {
                false
            }
        }
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
            key = "diskIoMode",
            label = "Disk I/O Mode (Linux/macOS)",
            description = "POSIX reopens files for every block and is slow on network storage. " +
                    "Memory-mapped is much faster but requires the JVM to run with signal chaining " +
                    "(LD_PRELOAD=/opt/java/openjdk/lib/libjsig.so), otherwise Gameyfin will crash.",
            type = TorrentDiskIoMode::class.java,
            default = TorrentDiskIoMode.POSIX
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
        // start() can be called again on a running plugin (e.g. after saving the config).
        // Shut the running tracker and client down first, otherwise the new tracker fails
        // with "Address already in use" and the plugin ends up FAILED.
        if (tracker != null || client != null) {
            log.info("Torrent plugin is already running, restarting tracker and client")
            stop()
        }

        Files.createDirectories(dataDirectory)

        // Remove leftovers of torrent creations that were interrupted (crash/restart)
        dataDirectory.listDirectoryEntries("*.torrent.tmp").forEach { it.deleteIfExists() }

        tracker = initTracker()

        client = initClient()

        state = loadState<TorrentDownloadPluginState>() ?: TorrentDownloadPluginState()

        // Restore existing torrents and remove invalid ones
        state.torrentFilesMetadata.removeIf { metadata ->
            val shouldRemove = !isUsableTorrentFile(metadata.torrentFile) ||
                    !Files.exists(metadata.gameFile) ||
                    metadata.gameFile.getLastModifiedTime().toInstant()
                        .isAfter(metadata.torrentFile.getLastModifiedTime().toInstant())

            if (shouldRemove) {
                true
            } else {
                try {
                    // These torrents were created from the files on disk: seed them without a
                    // full re-check, which on large games or network storage can take hours and
                    // blocks seeding of every other torrent in the meantime.
                    client?.addTorrent(metadata.torrentFile, metadata.gameFile, seedMode = true)
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
            stopSeedingWhenComplete = config("stopSeedingWhenComplete"),
            diskIoMode = optionalConfig<TorrentDiskIoMode>("diskIoMode") ?: TorrentDiskIoMode.POSIX
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
                errors["diskIoMode"] = " "
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

    internal fun getTrackerUri(): URI {
        val protocol = "http"
        val host = optionalConfig("externalHost") ?: InetAddress.getLocalHost().hostAddress
        val port = config<Int>("trackerPort")
        val path = "announce"

        return URI.create("$protocol://$host:$port/$path")
    }


    /**
     * Path of the torrent file of the given game files.
     */
    internal fun torrentFileFor(gameFilesPath: Path): Path =
        dataDirectory.resolve("${gameFilesPath.nameWithoutExtension}-${gameFilesPath.hashCode()}.torrent")

    /**
     * Makes sure an up-to-date torrent file exists for the given game files and that it is seeded.
     *
     * @param regenerateOutdated also regenerate the torrent file if the game files have been modified after it was
     * created. This requires walking the whole directory tree of the game, so it is only done on request.
     */
    internal fun ensureTorrent(gameFilesPath: Path, regenerateOutdated: Boolean = false): TorrentGenerationResult {
        val torrentFile = torrentFileFor(gameFilesPath)

        synchronized(lockFor(torrentFile)) {
            val trackerUrl = getTrackerUri().toString()
            val result = when {
                !Files.exists(torrentFile) -> TorrentGenerationResult.CREATED
                !isUsableTorrentFile(torrentFile, expectedTrackerUrl = trackerUrl) -> {
                    log.info("Regenerating torrent '${torrentFile.name}' (empty, unreadable or outdated tracker URL)")
                    TorrentGenerationResult.UPDATED
                }

                regenerateOutdated && isOutdated(gameFilesPath, torrentFile) -> {
                    log.info("Regenerating torrent '${torrentFile.name}' (game files have been modified)")
                    TorrentGenerationResult.UPDATED
                }

                else -> TorrentGenerationResult.UP_TO_DATE
            }

            if (result == TorrentGenerationResult.UPDATED) {
                // The old torrent might have a different info hash, stop seeding it
                removeFromSession(torrentFile)
            }

            if (result != TorrentGenerationResult.UP_TO_DATE) {
                // Write to a temporary file and move it into place atomically, so a crash or
                // restart during creation never leaves an empty/partial .torrent behind.
                val tempFile = torrentFile.resolveSibling("${torrentFile.name}.tmp")
                try {
                    Files.write(tempFile, torrentFileContent(gameFilesPath, trackerUrl))
                    Files.move(
                        tempFile, torrentFile,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
                    )
                } finally {
                    tempFile.deleteIfExists()
                }
            }

            synchronized(state) {
                if (state.torrentFilesMetadata.none { it.torrentFile == torrentFile }) {
                    state.torrentFilesMetadata.add(TorrentFileMetadata(torrentFile = torrentFile, gameFile = gameFilesPath))
                    saveState(state)
                }
            }

            // Add the torrent to the session for seeding. The torrent was just created from (or
            // matches) the files on disk, so skip the full hash check (seed mode).
            // This prevents crashes if there are permission issues or other errors
            try {
                client?.addTorrent(torrentFile, gameFilesPath, seedMode = true)
            } catch (e: Exception) {
                log.error("Failed to add torrent to seeding session - torrent file created but won't be seeded", e)
                // Don't rethrow - the torrent file was created successfully, seeding is optional
            }

            return result
        }
    }

    /**
     * Stops seeding the given torrent file, if it is currently seeded.
     */
    internal fun removeFromSession(torrentFile: Path) {
        try {
            client?.removeTorrent(torrentFile)
        } catch (e: Exception) {
            log.warn("Failed to remove torrent '${torrentFile.name}' from the seeding session", e)
        }
    }

    /**
     * Whether the game files (or anything within their directory) have been modified after the torrent file was created.
     */
    internal fun isOutdated(gameFilesPath: Path, torrentFile: Path): Boolean =
        latestModification(gameFilesPath).isAfter(torrentFile.getLastModifiedTime().toInstant())

    private fun latestModification(path: Path): Instant {
        if (!path.isDirectory()) return path.getLastModifiedTime().toInstant()
        return Files.walk(path).use { paths ->
            Instant.ofEpochMilli(paths.mapToLong { Files.getLastModifiedTime(it).toMillis() }.max().orElse(0))
        }
    }

    @Suppress("DEPRECATION")
    private fun torrentFileContent(gameFilesPath: Path, trackerUrl: String): ByteArray {
        val torrentBuilder = TorrentBuilder()

        val isPrivate = config<Boolean>("privateMode")
        val torrentVersions = config<TorrentVersion>("torrentVersions")

        log.info("Creating ${if (isPrivate) "private" else "public"} ${if (torrentVersions !== TorrentVersion.`V1 and V2`) torrentVersions else ""} torrent with announce URL '$trackerUrl'")

        val flags = when (torrentVersions) {
            TorrentVersion.`V1 only` -> v1_only
            TorrentVersion.`V2 only` -> v2_only
            TorrentVersion.`V1 and V2` -> null
        }

        val builder = torrentBuilder.path(gameFilesPath.toFile())
            .creator("Gameyfin Torrent plugin v${wrapper.descriptor.version}")
            .addTracker(trackerUrl)
            .setPrivate(isPrivate)

        if (flags != null) {
            builder.flags(flags)
        }

        val builderResult = builder.generate()

        return builderResult.entry().bencode()
    }

    @Extension(ordinal = 2)
    class TorrentDownloadProvider : DownloadProvider {
        private val log = LoggerFactory.getLogger(TorrentDownloadProvider::class.java)

        override fun download(path: Path): Download {
            log.info("Providing torrent for '${path.name}'...")

            val (result, timeTaken) = measureTimedValue {
                plugin.ensureTorrent(path)
            }
            val torrentFile = plugin.torrentFileFor(path)

            when (result) {
                TorrentGenerationResult.UP_TO_DATE -> log.info("Using existing torrent '${torrentFile.name}'")
                else -> log.info("Created torrent '${torrentFile.name}' in ${timeTaken.asHumanReadable()}")
            }

            return FileDownload(
                data = torrentFile.inputStream(),
                fileExtension = "torrent",
                size = torrentFile.fileSize()
            )
        }
    }
}

enum class TorrentGenerationResult {
    CREATED,
    UPDATED,
    UP_TO_DATE
}
