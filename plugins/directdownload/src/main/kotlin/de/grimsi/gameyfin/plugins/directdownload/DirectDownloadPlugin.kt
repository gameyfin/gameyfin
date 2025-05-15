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
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory

class DirectDownloadPlugin(wrapper: PluginWrapper) : GameyfinPlugin(wrapper), Configurable {
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
        companion object {
            private val END_OF_QUEUE = Pair<ZipEntry, Path>(ZipEntry("__END__"), Paths.get(""))
        }

        override fun getDownloadSources(path: Path): Download {
            if (!path.exists()) throw IllegalArgumentException("Path $path does not exist")

            return FileDownload(
                data = readContentAsSingleFile(path),
                fileExtension = if (path.isDirectory()) "zip" else path.extension,
                size = path.fileSize()
            )
        }

        fun readContentAsSingleFile(path: Path): InputStream {
            if (path.isDirectory()) return zipFilesInPath(path)
            return Files.newInputStream(path, StandardOpenOption.READ)
        }

        private fun zipFilesInPath(path: Path): InputStream {
            val pipedIn = PipedInputStream(64 * 1024)
            val pipedOut = PipedOutputStream(pipedIn)
            val queue: BlockingQueue<Pair<ZipEntry, Path>?> = LinkedBlockingQueue()

            // Producer: walks the file tree and enqueues files
            Thread.startVirtualThread {
                try {
                    Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            val entry = ZipEntry(path.relativize(file).toString())
                            queue.put(entry to file)
                            return FileVisitResult.CONTINUE
                        }
                    })
                } finally {
                    queue.put(END_OF_QUEUE) // signal end
                }
            }

            // Consumer: zips files in parallel, but writes entries in order
            Thread {
                ZipOutputStream(pipedOut).use { zos ->
                    zos.setLevel(Deflater.NO_COMPRESSION)
                    while (true) {
                        val item = queue.take()
                        if (item === END_OF_QUEUE || item == null) break
                        val (entry, file) = item
                        zos.putNextEntry(entry)
                        Files.newInputStream(file, StandardOpenOption.READ).use { input ->
                            input.copyTo(zos, 128 * 1024)
                        }
                        zos.closeEntry()
                    }
                }
                pipedOut.close()
            }.start()

            return pipedIn
        }
    }
}