package de.grimsi.gameyfin.core.download

import de.grimsi.gameyfin.core.plugins.management.GameyfinPluginManager
import de.grimsi.gameyfin.pluginapi.download.Download
import de.grimsi.gameyfin.pluginapi.download.DownloadProvider
import org.springframework.stereotype.Service
import kotlin.io.path.Path

@Service
class DownloadService(
    private val pluginManager: GameyfinPluginManager,
) {
    private val downloadPlugins: List<DownloadProvider>
        get() = pluginManager.getExtensions(DownloadProvider::class.java)

    fun getProviders(): List<String> {
        return downloadPlugins.map { it.javaClass.name }
    }

    fun getDownloadElement(path: String, provider: String): Download {
        val provider = downloadPlugins.firstOrNull { it.javaClass.name == provider }
            ?: throw IllegalArgumentException("Download provider $provider not found")

        return provider.getDownloadSources(Path(path))
    }
}