package org.gameyfin.plugins.download.direct

import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object CompressedZip {
    fun stream(root: Path, mode: CompressionMode): InputStream {
        val files = Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) }.sorted().map { it to Files.size(it) }.toList()
        }.toList()
        return ArchiveStream.open(root) { output ->
            // ArchiveStream owns the pipe: finish the ZIP without closing it before publishing failures.
            val zip = ZipOutputStream(object : java.io.FilterOutputStream(output) {
                override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
                override fun close() = Unit
            })
            zip.setLevel(mode.deflaterLevel())
            zip.use {
                for ((file, size) in files) {
                    val entry = ZipEntry(root.relativize(file).toString().replace('\\', '/'))
                    entry.time = 0
                    zip.putNextEntry(entry)
                    val copied = Files.newInputStream(file).use { it.copyTo(zip) }
                    if (copied != size) throw IOException("File size changed during archive generation: $file")
                    zip.closeEntry()
                }
            }
        }
    }
}
