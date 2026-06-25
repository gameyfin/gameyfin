package org.gameyfin.plugins.download.direct

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoredZipTest {

    private fun sampleTree(): Path {
        val root = Files.createTempDirectory("gf-storedzip-")
        root.resolve("readme.txt").writeBytes("hello world".toByteArray())
        root.resolve("data").createDirectories()
        root.resolve("data/level.dat").writeBytes(ByteArray(5000) { (it % 256).toByte() })
        root.resolve("data/empty.bin").writeBytes(ByteArray(0))
        root.resolve("café").createDirectories()            // non-ASCII path component
        root.resolve("café/naïve.sav").writeBytes(ByteArray(123) { 7 })
        return root
    }

    private fun countBytes(input: InputStream): Long {
        var total = 0L
        val buf = ByteArray(64 * 1024)
        input.use { while (true) { val n = it.read(buf); if (n < 0) break; total += n } }
        return total
    }

    /** The load-bearing test: a wrong Content-Length breaks downloads. */
    @Test
    fun `predicted size equals actual streamed bytes`() {
        val root = sampleTree()
        val predicted = StoredZip.computeSize(root)
        val actual = countBytes(StoredZip.stream(root))
        assertEquals(actual, predicted, "Content-Length predictor must match the bytes actually emitted")
    }

    @Test
    fun `produced archive is a valid zip with correct entries and contents`() {
        val root = sampleTree()
        val expected = mapOf(
            "readme.txt" to "hello world".toByteArray(),
            "data/level.dat" to ByteArray(5000) { (it % 256).toByte() },
            "data/empty.bin" to ByteArray(0),
            "café/naïve.sav" to ByteArray(123) { 7 },
        )
        val seen = HashMap<String, ByteArray>()
        ZipInputStream(StoredZip.stream(root)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                if (e.isDirectory) continue
                seen[e.name] = zis.readBytes()
            }
        }
        assertEquals(expected.keys, seen.keys)
        for ((k, v) in expected) assertTrue(v.contentEquals(seen[k]), "content mismatch for $k")
    }

    /** Random-access open validates the central directory + (zip64) end-of-central-directory records. */
    @Test
    fun `archive parses via random-access ZipFile`() {
        val root = sampleTree()
        val tmp = Files.createTempFile("gf-zip-", ".zip")
        StoredZip.stream(root).use { input -> Files.newOutputStream(tmp).use { input.copyTo(it) } }
        ZipFile(tmp.toFile()).use { zf ->
            val files = zf.entries().toList().filter { !it.isDirectory }
            assertEquals(4, files.size)
        }
    }

    @Test
    fun `empty folder size is predicted exactly`() {
        val root = Files.createTempDirectory("gf-storedzip-empty-")
        assertEquals(countBytes(StoredZip.stream(root)), StoredZip.computeSize(root))
    }

    // --- ZIP64 layout math, exercised with synthetic sizes (no multi-GB I/O) ---

    @Test
    fun `planned size, no zip64`() {
        // one entry: local(30+5+0+100) + central(46+5+0) + eocd(22)
        assertEquals(208L, StoredZip.plannedSize(listOf(5), listOf(100L)))
    }

    @Test
    fun `planned size, single file over 4GB triggers zip64 on size and end records`() {
        // local 30+5+20+5e9 ; cd 46+5+20 ; +zip64eocd56 +loc20 +eocd22
        assertEquals(5_000_000_224L, StoredZip.plannedSize(listOf(5), listOf(5_000_000_000L)))
    }

    @Test
    fun `planned size, small file after 4GB gets zip64 offset field only`() {
        // big(name5,5e9) then small(name5,10): the small entry's local offset > 4GB
        // → central zip64 holds only the 8-byte offset (size fits in 32 bits).
        assertEquals(5_000_000_332L, StoredZip.plannedSize(listOf(5, 5), listOf(5_000_000_000L, 10L)))
    }

    /**
     * Real >4 GiB archive: validates the writer's ZIP64 path end to end (exact size +
     * readability). Gated behind GAMEYFIN_BIGTEST=true because it moves multiple GiB.
     */
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "GAMEYFIN_BIGTEST", matches = "true")
    @Test
    fun `archive over 4GB streams with exact size and stays readable`() {
        val root = Files.createTempDirectory("gf-storedzip-big-")
        val big = root.resolve("big.bin")
        java.io.RandomAccessFile(big.toFile(), "rw").use { it.setLength(4_300_000_000L) } // sparse, > 4 GiB
        root.resolve("after.txt").writeBytes("tail".toByteArray())

        assertEquals(countBytes(StoredZip.stream(root)), StoredZip.computeSize(root))

        var seenBig = false
        var seenAfter = false
        ZipInputStream(StoredZip.stream(root)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                when (e.name) {
                    "big.bin" -> seenBig = true
                    "after.txt" -> seenAfter = true
                }
                zis.closeEntry()
            }
        }
        assertTrue(seenBig && seenAfter, "both entries present in the >4GB archive")
    }
}
