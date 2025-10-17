package org.gameyfin.app.core.download

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.plugins.management.GameyfinPluginDescriptor
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.app.games.entities.Game
import org.gameyfin.pluginapi.download.Download
import org.gameyfin.pluginapi.download.DownloadProvider
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.io.InputStream
import java.io.OutputStream
import kotlin.io.path.Path
import kotlin.time.DurationUnit
import kotlin.time.measureTime

@Service
class DownloadService(
    private val pluginManager: GameyfinPluginManager,
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

    @Async("virtualThreadPoolTaskExecutor")
    fun processDownload(data: InputStream, outputStream: OutputStream, game: Game, username: String?) {
        log.debug { "User '${username ?: "unknown user"}' started download for game '${game.title}' [ID ${game.id}]" }

        val timeTaken = measureTime {
            data.copyTo(outputStream)
        }

        log.debug {
            "Download of game '${game.title}' [ID ${game.id}] by user '${username ?: "unknown user"}' " +
                    "completed in ${timeTaken.toString(DurationUnit.SECONDS)}"
        }
    }
}