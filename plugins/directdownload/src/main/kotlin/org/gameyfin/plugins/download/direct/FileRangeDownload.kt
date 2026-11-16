package org.gameyfin.plugins.download.direct

import org.gameyfin.pluginapi.download.DownloadMetadata
import org.gameyfin.pluginapi.download.RangeDownload
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import kotlin.io.path.extension

/** Files must remain unchanged during delivery; replacements must update identity or timestamps. */
internal class FileRangeDownload(private val path: Path) : RangeDownload {
    private val before = fingerprint(path)
    private val channel = FileChannel.open(path, StandardOpenOption.READ)
    override val metadata: DownloadMetadata
    override val etag: String

    init {
        try {
            if (before != fingerprint(path)) throw IOException("File changed while opening $path")
            metadata = DownloadMetadata(path.extension, channel.size())
            etag = "\"" + MessageDigest.getInstance("SHA-256")
                .digest(before.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } + "\""
        } catch (error: Throwable) {
            channel.close()
            throw error
        }
    }

    override fun openStream(offset: Long, length: Long): InputStream {
        require(offset >= 0 && length >= 0 && offset <= metadata.size!! && length <= metadata.size!! - offset)
        if (before != fingerprint(path)) throw IOException("File changed before delivery: $path")
        channel.position(offset)
        return object : InputStream() {
            private var remaining = length
            override fun read(): Int {
                val byte = ByteArray(1)
                return if (read(byte, 0, 1) < 0) -1 else byte[0].toInt() and 255
            }
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                java.util.Objects.checkFromIndexSize(off, len, bytes.size)
                if (len == 0) return 0
                if (remaining == 0L) return -1
                val count = channel.read(ByteBuffer.wrap(bytes, off, minOf(len.toLong(), remaining).toInt()))
                if (count < 0) throw IOException("File shrank during delivery: $path")
                remaining -= count
                return count
            }
            override fun close() = this@FileRangeDownload.close()
        }
    }

    override fun close() = channel.close()

    private fun fingerprint(path: Path): String {
        val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
        return listOf(path.toRealPath(), attrs.fileKey(), attrs.size(), attrs.lastModifiedTime(), attrs.creationTime())
            .joinToString("\u0000")
    }
}
