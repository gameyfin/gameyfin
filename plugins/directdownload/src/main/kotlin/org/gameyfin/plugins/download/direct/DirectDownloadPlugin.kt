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
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
            default = CompressionMode.NONE
        )
    )

    @Extension(ordinal = 1)
    class DirectDownloadProvider : DownloadProvider {
        override fun download(path: Path): Download {
            if (!path.exists()) throw IllegalArgumentException("Path $path does not exist")

            return FileDownload(
                data = streamContentAsSingleFile(path),
                fileExtension = if (path.isDirectory()) "zip" else path.extension,
                size = path.fileSize()
            )
        }

        fun streamContentAsSingleFile(path: Path): InputStream {
            if (path.isDirectory()) return streamFolderAsZip(path)
            return streamFile(path)
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

        fun streamFolderAsZip(path: Path): InputStream {
            val pipeIn = PipedInputStream(512 * 1024) // 512 KB buffer
            val pipeOut = PipedOutputStream(pipeIn)

            Thread.ofVirtual().start {
                try {
                    ZipOutputStream(pipeOut).use { zos ->

                        val compressionMode = plugin.config<CompressionMode>("compressionMode")
                        zos.setLevel(compressionMode.deflaterLevel())

                        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                                val entry = ZipEntry(path.relativize(file).toString())
                                zos.putNextEntry(entry)
                                Files.newInputStream(file, StandardOpenOption.READ).use { input ->
                                    input.copyTo(zos, 512 * 1024)
                                }
                                zos.closeEntry()
                                return FileVisitResult.CONTINUE
                            }
                        })
                    }
                    pipeOut.close()
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