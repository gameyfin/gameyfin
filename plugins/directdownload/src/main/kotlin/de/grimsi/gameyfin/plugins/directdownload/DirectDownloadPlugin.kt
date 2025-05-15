package de.grimsi.gameyfin.plugins.directdownload

import de.grimsi.gameyfin.pluginapi.core.Configurable
import de.grimsi.gameyfin.pluginapi.core.GameyfinPlugin
import de.grimsi.gameyfin.pluginapi.core.PluginConfigElement
import de.grimsi.gameyfin.pluginapi.download.Download
import de.grimsi.gameyfin.pluginapi.download.DownloadProvider
import de.grimsi.gameyfin.pluginapi.download.FileDownload
import org.pf4j.Extension
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory

class DirectDownloadPlugin(wrapper: PluginWrapper) : GameyfinPlugin(wrapper), Configurable {
    companion object {
        lateinit var plugin: DirectDownloadPlugin
            private set
    }

    init {
        plugin = this
    }

    val log: Logger = LoggerFactory.getLogger(javaClass)

    enum class CompressionMode {
        NONE,
        FAST,
        BEST;

        companion object {
            fun toDeflaterLevel(mode: CompressionMode): Int {
                return when (mode) {
                    NONE -> Deflater.NO_COMPRESSION
                    FAST -> Deflater.BEST_SPEED
                    BEST -> Deflater.BEST_COMPRESSION
                }
            }
        }
    }

    override val configMetadata: List<PluginConfigElement> = listOf(
        PluginConfigElement(
            key = "compressionMode",
            name = "Compression mode for generated ZIP files (\"none\", \"fast\", \"best\")",
            description = "Higher compression uses more CPU but saves bandwidth",
        )
    )

    override var config: Map<String, String?> = emptyMap()

    override fun validateConfig(config: Map<String, String?>): Boolean {
        return config["compressionMode"]?.let {
            try {
                CompressionMode.valueOf(it.uppercase())
                true
            } catch (_: IllegalArgumentException) {
                log.error("Invalid compression mode: $it")
                false
            }
        } ?: true
    }

    @Extension
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

                        zos.setLevel(CompressionMode.toDeflaterLevel(plugin.config["compressionMode"]?.let {
                            CompressionMode.valueOf(it.uppercase())
                        } ?: CompressionMode.NONE))

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