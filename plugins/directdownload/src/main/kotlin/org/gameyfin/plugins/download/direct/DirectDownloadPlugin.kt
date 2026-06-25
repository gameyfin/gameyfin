package org.gameyfin.plugins.download.direct

import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.gameyfin.pluginapi.core.config.PluginConfigMetadata
import org.gameyfin.pluginapi.core.wrapper.ConfigurableGameyfinPlugin
import org.gameyfin.pluginapi.download.Download
import org.gameyfin.pluginapi.download.DownloadProvider
import org.gameyfin.pluginapi.download.FileDownload
import org.pf4j.Extension
import org.pf4j.PluginWrapper
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory

class DirectDownloadPlugin(wrapper: PluginWrapper) : ConfigurableGameyfinPlugin(wrapper) {

    companion object {
        lateinit var plugin: DirectDownloadPlugin
            private set
    }

    init {
        plugin = this
    }

    override val configMetadata: PluginConfigMetadata = listOf(
        ConfigMetadata(
            key = "compressionMode",
            type = CompressionMode::class.java,
            label = "Compression mode",
            description = "Higher compression modes are more resource intensive, but save bandwidth",
            default = CompressionMode.None
        )
    )

    @Suppress("Unused")
    @Extension(ordinal = 1)
    class DirectDownloadProvider : DownloadProvider {
        override fun download(path: Path): Download {
            if (!path.exists()) throw IllegalArgumentException("Path $path does not exist")

            val isDirectory = path.isDirectory()
            return FileDownload(
                // Folders are packed as a STORED zip whose size is known up front, so the
                // download advertises a Content-Length (browser shows size + progress bar).
                // See StoredZip for the rationale and the ZIP64 handling.
                data = if (isDirectory) StoredZip.stream(path) else streamFile(path),
                fileExtension = if (isDirectory) "zip" else path.extension,
                size = if (isDirectory) StoredZip.computeSize(path) else path.fileSize()
            )
        }

        fun streamFile(path: Path): InputStream {
            val pipeIn = PipedInputStream(512 * 1024)
            val pipeOut = PipedOutputStream(pipeIn)

            Thread.ofVirtual().start {
                try {
                    Files.newInputStream(path, StandardOpenOption.READ).use { input ->
                        input.copyTo(pipeOut, 512 * 1024)
                    }
                } catch (_: IOException) {
                } finally {
                    try {
                        pipeOut.close()
                    } catch (_: IOException) {
                    }
                }
            }

            return pipeIn
        }
    }
}
