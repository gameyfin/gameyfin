package org.gameyfin.app.core.plugins.marketplace

import org.pf4j.update.FileDownloader
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

/**
 * Downloads plugin files into [downloadDirectory] (which should be on the same file system as the plugin root,
 * so the downloaded file can be moved atomically).
 */
class GameyfinFileDownloader(
    private val httpClient: MarketplaceHttpClient,
    private val downloadDirectory: () -> Path,
) : FileDownloader {

    override fun downloadFile(fileUrl: URL): Path {
        val directory = Files.createDirectories(downloadDirectory())
        val target = Files.createTempFile(directory, "plugin-", ".jar.part")
        httpClient.download(fileUrl.toURI(), target, MarketplaceHttpClient.MAX_PLUGIN_SIZE)
        return target
    }
}
