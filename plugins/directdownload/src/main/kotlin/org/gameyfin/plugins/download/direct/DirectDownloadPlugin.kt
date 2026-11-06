package org.gameyfin.plugins.download.direct

import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.gameyfin.pluginapi.core.config.PluginConfigMetadata
import org.gameyfin.pluginapi.core.wrapper.ConfigurableGameyfinPlugin
import org.gameyfin.pluginapi.core.wrapper.PluginContext
import org.gameyfin.pluginapi.download.*
import org.pf4j.Extension
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory

class DirectDownloadPlugin(context: PluginContext) : ConfigurableGameyfinPlugin(context) {

    companion object {
        lateinit var plugin: DirectDownloadPlugin
            private set
    }

    init {
        plugin = this
    }

    override val configMetadata: PluginConfigMetadata = listOf(
        ConfigMetadata(
            key = "calculateArchiveSize",
            type = Boolean::class.java,
            label = "Calculate folder download size",
            description = "Scan folders before downloading to show exact size and percentage progress.\nThis can delay startup for large file trees.",
            default = false
        ),
        ConfigMetadata(
            key = "compressionMode",
            type = CompressionMode::class.java,
            label = "Compression mode",
            description = "Compression applies to uncompressed folders only.\nHigher modes use more CPU but save bandwidth.",
            default = CompressionMode.None
        )
    )

    @Suppress("Unused")
    @Extension(ordinal = 1)
    class DirectDownloadProvider : DownloadProvider {
        private fun mode() = plugin.config<CompressionMode>("compressionMode")
        private fun calculateSize() = plugin.config<Boolean>("calculateArchiveSize")

        override fun describe(path: Path): DownloadMetadata =
            if (path.isDirectory()) DownloadMetadata(
                "zip",
                if (mode() == CompressionMode.None && calculateSize()) StoredZip.computeSize(path) else null
            )
            else DownloadMetadata(path.extension, path.fileSize())

        override fun prepareRangeDownload(path: Path): RangeDownload? =
            if (path.isDirectory()) null else FileRangeDownload(path)

        override fun download(path: Path): Download {
            require(path.exists()) { "Path $path does not exist" }
            if (!path.isDirectory()) {
                val size = path.fileSize()
                return FileDownload(streamFile(path), path.extension, size)
            }
            val mode = mode()
            return if (mode == CompressionMode.None) {
                if (calculateSize()) StoredZip.download(path) else FileDownload(StoredZip.stream(path), "zip")
            } else FileDownload(CompressedZip.stream(path, mode), "zip")
        }

        fun streamFile(path: Path): InputStream = Files.newInputStream(path)
    }
}
