package org.gameyfin.plugins.download.torrent

import com.frostwire.jlibtorrent.TorrentInfo
import org.gameyfin.pluginapi.core.wrapper.PluginContext
import org.gameyfin.pluginapi.data.GameyfinDataService
import org.gameyfin.pluginapi.download.FileDownload
import org.gameyfin.pluginapi.download.LinkDownload
import org.gameyfin.pluginapi.notifications.GameyfinNotificationService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLDecoder
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

class TorrentDownloadPluginTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `file and magnet providers share cached torrents for every protocol version`() {
        for (version in TorrentVersion.entries) {
            val data = directory.resolve(version.name).createDirectories()
            val game = data.resolve("Game ü & extra.bin").apply { writeBytes(ByteArray(32_768) { it.toByte() }) }
            val plugin = TorrentDownloadPlugin(object : PluginContext {
                override val pluginId = "torrent-test"
                override val pluginVersion = "test"
                override val dataDirectory = data
                override val dataService: GameyfinDataService get() = error("Unused")
                override val notificationService: GameyfinNotificationService get() = error("Unused")
            })
            plugin.loadConfig(mapOf("externalHost" to "tracker.test", "torrentVersions" to version.name))
            TorrentDownloadPlugin.state = TorrentDownloadPluginState()

            val torrent = assertIs<FileDownload>(TorrentDownloadPlugin.TorrentDownloadProvider().download(game))
            val fileBytes = torrent.data.use { it.readAllBytes() }
            assertEquals("torrent", torrent.fileExtension)
            assertEquals(fileBytes.size.toLong(), torrent.size)
            val cached = plugin.torrentFileFor(game)
            val modified = cached.getLastModifiedTime()
            val info = TorrentInfo(cached.toFile())
            assertTrue(info.isPrivate)

            val link = assertIs<LinkDownload>(TorrentDownloadPlugin.MagnetDownloadProvider().download(game))
            assertEquals(info.makeMagnetUri(), link.url)
            val parameters = link.url.removePrefix("magnet:?").split('&').map {
                val (key, value) = it.split('=', limit = 2)
                key to URLDecoder.decode(value, Charsets.UTF_8)
            }.groupBy({ it.first }, { it.second })
            assertEquals(listOf(game.name), parameters["dn"])
            assertEquals(listOf(plugin.getTrackerUri().toString()), parameters["tr"])
            val hashes = parameters.getValue("xt")
            assertEquals(version != TorrentVersion.`V2 only`, hashes.any { it.startsWith("urn:btih:") })
            assertEquals(version != TorrentVersion.`V1 only`, hashes.any { it.startsWith("urn:btmh:") })
            assertEquals(modified, cached.getLastModifiedTime())
            assertContentEquals(fileBytes, cached.readBytes())
            assertEquals(1, TorrentDownloadPlugin.state.torrentFilesMetadata.size)

            // A HEAD probe must not create metadata or start preparing a download.
            val missing = data.resolve("missing")
            assertNull(TorrentDownloadPlugin.MagnetDownloadProvider().describe(missing))
            assertFalse(plugin.torrentFileFor(missing).exists())
        }
    }

    @Test
    fun `both download extensions are registered`() {
        val extensions = javaClass.classLoader.getResourceAsStream("META-INF/extensions.idx")!!
            .bufferedReader().use { it.readText() }
        assertTrue(extensions.contains(TorrentDownloadPlugin.TorrentDownloadProvider::class.java.name))
        assertTrue(extensions.contains(TorrentDownloadPlugin.MagnetDownloadProvider::class.java.name))
    }
}
