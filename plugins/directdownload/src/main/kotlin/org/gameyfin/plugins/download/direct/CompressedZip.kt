package org.gameyfin.plugins.download.direct

import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object CompressedZip {
    fun stream(
        root: Path, mode: CompressionMode,
        visit: (Path, (Path, Long) -> Unit) -> Unit = ArchiveFiles::visit
    ): InputStream {
        return ArchiveStream.open(root) { output ->
            // ArchiveStream owns the pipe: finish the ZIP without closing it before publishing failures.
            val zip = ZipOutputStream(object : java.io.FilterOutputStream(output) {
                override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
                override fun close() = Unit
            })
            zip.setLevel(mode.deflaterLevel())
            zip.use {
                var firstEntry = true
                visit(root) { file, size ->
                    val entry = ZipEntry(root.relativize(file).toString().replace('\\', '/'))
                    entry.time = 0
                    zip.putNextEntry(entry)
                    if (firstEntry) {
                        zip.flush()
                        firstEntry = false
                    }
                    val copied = Files.newInputStream(file).use { it.copyTo(zip) }
                    if (copied != size) throw IOException("File size changed during archive generation: $file")
                    zip.closeEntry()
                }
            }
        }
    }
}
