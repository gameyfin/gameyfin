package org.gameyfin.plugins.download.direct

import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32

/**
 * Packs a folder into a STORED (uncompressed) ZIP whose exact byte length can be
 * computed up front, so a folder download can advertise a Content-Length.
 *
 * Why STORED: a DEFLATE stream's length is unknown until it finishes, so the original
 * plugin sent no Content-Length for folder downloads (the browser showed "unknown size").
 * STORED makes the archive length a pure function of the file sizes and names. Game
 * payloads are already-compressed, so dropping DEFLATE costs ~no bandwidth.
 *
 * ZIP64 is added per-entry **only as needed** (a file >= 4 GiB, or a local-header offset
 * past 4 GiB in a large archive) — forcing it onto every entry produces archives that
 * modern unzip tools and JDK 22+ reject. [computeSize] and [stream] share the same layout
 * helpers ([localExtraLen]/[centralExtraLen]/[needsZip64End]), so the advertised size is
 * correct by construction.
 */
object StoredZip {

    private const val ZIP64_MAGIC = 0xFFFFFFFFL   // value at/above this needs a ZIP64 field
    private const val U16_MAX = 0xFFFF

    private const val LOCAL_HEADER = 30L
    private const val CENTRAL_HEADER = 46L
    private const val EOCD = 22L
    private const val ZIP64_EOCD = 56L
    private const val ZIP64_LOCATOR = 20L

    private class Entry(val file: Path, val name: ByteArray, val size: Long)

    /** Bytes of the local-header ZIP64 extra field (id+len + uncompressed + compressed), or 0. */
    private fun localExtraLen(size: Long): Long = if (size >= ZIP64_MAGIC) 20L else 0L

    /** Bytes of the central-header ZIP64 extra field, holding only the fields that overflow. */
    private fun centralExtraLen(size: Long, offset: Long): Long {
        val sizeOver = size >= ZIP64_MAGIC
        val offsetOver = offset >= ZIP64_MAGIC
        if (!sizeOver && !offsetOver) return 0L
        return 4L + (if (sizeOver) 16L else 0L) + (if (offsetOver) 8L else 0L)
    }

    private fun needsZip64End(count: Long, cdStart: Long, cdSize: Long): Boolean =
        count >= U16_MAX || cdStart >= ZIP64_MAGIC || cdSize >= ZIP64_MAGIC

    /** Exact number of bytes [stream] will emit for [root]. */
    fun computeSize(root: Path): Long {
        val entries = entries(root)
        return plannedSize(entries.map { it.name.size }, entries.map { it.size })
    }

    /**
     * Exact archive byte length for entries with the given name-byte lengths and file sizes,
     * in order. Pure (no I/O) so the ZIP64 math is testable with synthetic multi-GB sizes.
     * Must stay in lockstep with [writeArchive].
     */
    internal fun plannedSize(nameBytes: List<Int>, sizes: List<Long>): Long {
        var offset = 0L
        for (i in sizes.indices) offset += LOCAL_HEADER + nameBytes[i] + localExtraLen(sizes[i]) + sizes[i]
        val cdStart = offset

        var cdSize = 0L
        var localOffset = 0L
        for (i in sizes.indices) {
            cdSize += CENTRAL_HEADER + nameBytes[i] + centralExtraLen(sizes[i], localOffset)
            localOffset += LOCAL_HEADER + nameBytes[i] + localExtraLen(sizes[i]) + sizes[i]
        }

        val end = if (needsZip64End(sizes.size.toLong(), cdStart, cdSize)) ZIP64_EOCD + ZIP64_LOCATOR + EOCD else EOCD
        return cdStart + cdSize + end
    }

    /** Streams [root] as a STORED zip on a virtual thread via a pipe. */
    fun stream(root: Path): InputStream {
        val pipeIn = PipedInputStream(512 * 1024)
        val pipeOut = PipedOutputStream(pipeIn)
        val entries = entries(root)

        Thread.ofVirtual().start {
            try {
                BufferedOutputStream(pipeOut, 512 * 1024).use { out -> writeArchive(out, entries) }
            } catch (_: IOException) {
                // Client disconnected mid-download — nothing to do; pipe closed below.
            } finally {
                try {
                    pipeOut.close()
                } catch (_: IOException) {
                }
            }
        }

        return pipeIn
    }

    private class Central(val name: ByteArray, val crc: Long, val size: Long, val offset: Long)

    private fun writeArchive(out: OutputStream, entries: List<Entry>) {
        val centrals = ArrayList<Central>(entries.size)
        var offset = 0L

        // Local headers + file data
        for (e in entries) {
            val crc = crc32(e.file)
            val zip64 = e.size >= ZIP64_MAGIC
            out.u32(0x04034b50)                          // local file header signature
            out.u16(if (zip64) 45 else 20)               // version needed
            out.u16(0x0800)                              // flags: UTF-8 names
            out.u16(0)                                   // method: STORED
            out.u16(0); out.u16(0x21)                    // mod time / date (1980-01-01)
            out.u32(crc)
            out.u32(if (zip64) ZIP64_MAGIC else e.size)  // compressed size
            out.u32(if (zip64) ZIP64_MAGIC else e.size)  // uncompressed size
            out.u16(e.name.size)
            out.u16(localExtraLen(e.size).toInt())
            out.write(e.name)
            if (zip64) { out.u16(1); out.u16(16); out.u64(e.size); out.u64(e.size) }

            Files.newInputStream(e.file).use { input -> input.copyTo(out, 512 * 1024) }

            centrals.add(Central(e.name, crc, e.size, offset))
            offset += LOCAL_HEADER + e.name.size + localExtraLen(e.size) + e.size
        }

        // Central directory
        val cdStart = offset
        var cdSize = 0L
        for (c in centrals) {
            val sizeOver = c.size >= ZIP64_MAGIC
            val offsetOver = c.offset >= ZIP64_MAGIC
            val extra = centralExtraLen(c.size, c.offset)
            out.u32(0x02014b50)                              // central file header signature
            out.u16(45)                                      // version made by
            out.u16(if (sizeOver || offsetOver) 45 else 20)  // version needed
            out.u16(0x0800)                                  // flags: UTF-8 names
            out.u16(0)                                       // method: STORED
            out.u16(0); out.u16(0x21)                        // mod time / date
            out.u32(c.crc)
            out.u32(if (sizeOver) ZIP64_MAGIC else c.size)   // compressed size
            out.u32(if (sizeOver) ZIP64_MAGIC else c.size)   // uncompressed size
            out.u16(c.name.size)
            out.u16(extra.toInt())
            out.u16(0)                                       // comment length
            out.u16(0)                                       // disk number start
            out.u16(0)                                       // internal attributes
            out.u32(0)                                       // external attributes
            out.u32(if (offsetOver) ZIP64_MAGIC else c.offset)
            out.write(c.name)
            if (extra > 0) {
                out.u16(1); out.u16((extra - 4).toInt())
                if (sizeOver) { out.u64(c.size); out.u64(c.size) }
                if (offsetOver) out.u64(c.offset)
            }
            cdSize += CENTRAL_HEADER + c.name.size + extra
        }

        // End-of-central-directory records
        val count = centrals.size.toLong()
        if (needsZip64End(count, cdStart, cdSize)) {
            out.u32(0x06064b50)                  // ZIP64 end of central directory record
            out.u64(44)                          // size of remainder of this record
            out.u16(45); out.u16(45)             // version made by / needed
            out.u32(0); out.u32(0)               // this disk / disk with CD start
            out.u64(count); out.u64(count)       // entries on disk / total entries
            out.u64(cdSize); out.u64(cdStart)    // size of CD / offset of CD
            out.u32(0x07064b50)                  // ZIP64 end of central directory locator
            out.u32(0)                           // disk with ZIP64 EOCD
            out.u64(cdStart + cdSize)            // offset of ZIP64 EOCD
            out.u32(1)                           // total number of disks
        }
        out.u32(0x06054b50)                      // end of central directory record
        out.u16(0); out.u16(0)                   // this disk / disk with CD start
        out.u16(if (count >= U16_MAX) U16_MAX else count.toInt())   // entries on disk
        out.u16(if (count >= U16_MAX) U16_MAX else count.toInt())   // total entries
        out.u32(if (cdSize >= ZIP64_MAGIC) ZIP64_MAGIC else cdSize)
        out.u32(if (cdStart >= ZIP64_MAGIC) ZIP64_MAGIC else cdStart)
        out.u16(0)                               // comment length
    }

    /** Regular files under [root], deterministically ordered. Empty directories are not entries. */
    private fun entries(root: Path): List<Entry> {
        val files = ArrayList<Path>()
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { files.add(it) }
        }
        return files
            .map { Entry(it, zipName(root, it).toByteArray(Charsets.UTF_8), Files.size(it)) }
            .sortedWith(compareBy { String(it.name, Charsets.UTF_8) })
    }

    private fun zipName(root: Path, file: Path): String =
        root.relativize(file).toString().replace(File.separatorChar, '/')

    private fun crc32(file: Path): Long {
        val crc = CRC32()
        Files.newInputStream(file).use { input ->
            val buf = ByteArray(512 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                crc.update(buf, 0, n)
            }
        }
        return crc.value
    }

    private fun OutputStream.u16(v: Int) {
        write(v and 0xFF); write((v ushr 8) and 0xFF)
    }

    private fun OutputStream.u32(v: Long) {
        write((v and 0xFF).toInt()); write(((v ushr 8) and 0xFF).toInt())
        write(((v ushr 16) and 0xFF).toInt()); write(((v ushr 24) and 0xFF).toInt())
    }

    private fun OutputStream.u64(v: Long) {
        for (i in 0 until 8) write(((v ushr (8 * i)) and 0xFF).toInt())
    }
}
