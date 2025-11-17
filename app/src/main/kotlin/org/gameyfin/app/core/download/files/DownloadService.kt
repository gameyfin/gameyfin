package org.gameyfin.app.core.download.files

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.download.bandwidth.SessionBandwidthManager
import org.gameyfin.app.core.download.bandwidth.SessionMonitoredOutputStream
import org.gameyfin.app.core.download.bandwidth.SessionThrottledOutputStream
import org.gameyfin.app.core.download.provider.DownloadProviderDto
import org.gameyfin.app.core.plugins.management.GameyfinPluginDescriptor
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.app.games.entities.Game
import org.gameyfin.pluginapi.download.Download
import org.gameyfin.pluginapi.download.DownloadProvider
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

        val finalOutputStream = if (maxBytesPerSecond > 0) {
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

        try {
            finalOutputStream.use {
                val timeTaken = measureTime {
                    data.copyTo(finalOutputStream)
                    finalOutputStream.flush()
                }

                log.debug {
                    "Download of game '${game.title}' [ID ${game.id}] by user '${username ?: "anonymous user"}' " +
                            "(session: $sessionId) completed in ${timeTaken.toString(DurationUnit.SECONDS)}"
                }
            }
        } catch (e: IOException) {
            // Client disconnected (cancelled download, network error, etc.)
            // This is expected behavior, log at debug level instead of error
            log.debug {
                "Download of game '${game.title}' [ID ${game.id}] by user '${username ?: "anonymous user"}' " +
                        "(session: $sessionId) was interrupted: ${e.message}"
            }
            // Don't re-throw - this is expected when clients cancel downloads
        }
    }
}