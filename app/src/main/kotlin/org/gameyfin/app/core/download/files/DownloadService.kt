package org.gameyfin.app.core.download.files

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.download.bandwidth.SessionBandwidthManager
import org.gameyfin.app.core.download.bandwidth.SessionMonitoredOutputStream
import org.gameyfin.app.core.download.bandwidth.SessionThrottledOutputStream
import org.gameyfin.app.core.download.provider.DownloadProviderDto
import org.gameyfin.app.core.metrics.DownloadMetrics
import org.gameyfin.app.core.plugins.management.GameyfinPluginDescriptor
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.app.games.entities.Game
import org.gameyfin.pluginapi.download.Download
import org.gameyfin.pluginapi.download.DownloadProvider
import org.gameyfin.pluginapi.download.RangeDownload
import org.gameyfin.pluginapi.download.DownloadMetadata
import org.springframework.stereotype.Service
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.io.path.Path
import kotlin.time.DurationUnit
import kotlin.time.measureTime

@Service
class DownloadService(
    private val pluginManager: GameyfinPluginManager,
    private val configService: ConfigService,
    private val sessionBandwidthManager: SessionBandwidthManager,
    private val downloadMetrics: DownloadMetrics,
) {

    companion object {
        private val log = KotlinLogging.logger { }
    }

    private val downloadPlugins: List<DownloadProvider>
        get() = pluginManager.getExtensions(DownloadProvider::class.java)

    fun getProviders(): List<DownloadProviderDto> {
        return downloadPlugins.map {
            val plugin = pluginManager.whichPlugin(it.javaClass.enclosingClass)
            val managementEntry = pluginManager.getManagementEntry(plugin.pluginId)
            val descriptor = plugin.descriptor as GameyfinPluginDescriptor

            DownloadProviderDto(
                key = it.javaClass.name,
                name = descriptor.pluginName,
                priority = managementEntry.priority,
                description = descriptor.pluginDescription,
                shortDescription = descriptor.pluginShortDescription,
            )
        }
    }

    fun getDownload(path: String, provider: String): Download {
        val provider = downloadPlugins.firstOrNull { it.javaClass.name == provider }
            ?: throw IllegalArgumentException("Download provider $provider not found")

        return provider.download(Path(path))
    }

    fun getRangeDownload(path: String, provider: String): RangeDownload? =
        findProvider(provider).prepareRangeDownload(Path(path))

    fun describeDownload(path: String, provider: String): DownloadMetadata? =
        findProvider(provider).describe(Path(path))

    private fun findProvider(provider: String): DownloadProvider =
        downloadPlugins.firstOrNull { it.javaClass.name == provider }
            ?: throw IllegalArgumentException("Download provider $provider not found")

    fun processDownload(
        data: InputStream,
        outputStream: OutputStream,
        game: Game,
        username: String?,
        sessionId: String,
        remoteIp: String
    ) {
        log.debug { "User '${username ?: "unknown user"}' (session: $sessionId) started download for game '${game.title}' [ID ${game.id}]" }

        val bandwidthLimitEnabled = configService.get(ConfigProperties.Downloads.BandwidthLimitEnabled) ?: false
        val bandwidthLimitMbps = configService.get(ConfigProperties.Downloads.BandwidthLimitMbps) ?: 0

        // Convert Mbps to bytes per second (1 Mbps = 125,000 bytes/second)
        val maxBytesPerSecond = if (bandwidthLimitEnabled && bandwidthLimitMbps > 0) {
            (bandwidthLimitMbps * 125_000).toLong()
        } else {
            0L // 0 means unlimited
        }

        // Always get a tracker to enable stats monitoring, even without throttling
        val tracker = sessionBandwidthManager.getTracker(sessionId, maxBytesPerSecond)

        val throttled = maxBytesPerSecond > 0

        val finalOutputStream = if (throttled) {
            log.debug {
                "Applying session-based bandwidth limit of $bandwidthLimitMbps Mbps ($maxBytesPerSecond bytes/sec) " +
                        "for download of '${game.title}' (active downloads for this session: ${tracker.activeDownloads.get()})"
            }
            SessionThrottledOutputStream(outputStream, tracker, game.id, username, remoteIp)
        } else {
            log.debug {
                "Monitoring download of '${game.title}' without bandwidth limit " +
                        "(active downloads for this session: ${tracker.activeDownloads.get()})"
            }
            SessionMonitoredOutputStream(outputStream, tracker, game.id, username, remoteIp)
        }

        downloadMetrics.recordDownloadStarted(throttled)

        var bytesWritten = 0L
        try {
            // Close the source as well, so its resources are released when the client cancels the download
            data.use {
                // The servlet owns the response stream. Closing it on a source error could
                // finalize a truncated chunked response before Spring handles the failure.
                val timeTaken = measureTime {
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = readDownloadSource(data, buffer, game)
                        if (count < 0) break
                        finalOutputStream.write(buffer, 0, count)
                        bytesWritten += count
                    }
                    finalOutputStream.flush()
                }

                downloadMetrics.recordDownloadCompleted(bytesWritten)

                log.debug {
                    "Download of game '${game.title}' [ID ${game.id}] by user '${username ?: "anonymous user"}' " +
                            "(session: $sessionId) completed in ${timeTaken.toString(DurationUnit.SECONDS)}"
                }
            }
        } catch (e: IOException) {
            downloadMetrics.recordDownloadFailed()

            // Client disconnected (cancelled download, network error, etc.)
            // This is expected behavior, log at debug level instead of error
            log.debug {
                "Download of game '${game.title}' [ID ${game.id}] by user '${username ?: "anonymous user"}' " +
                        "(session: $sessionId) was interrupted: ${e.message}"
            }
            throw e
        } finally {
            tracker.downloadCompleted(game.id)
        }
    }

    private fun readDownloadSource(data: InputStream, buffer: ByteArray, game: Game): Int =
        try {
            data.read(buffer)
        } catch (error: IOException) {
            log.warn(error) { "Failed reading download source '${game.metadata.path}'" }
            throw error
        }
}
