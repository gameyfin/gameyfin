package org.gameyfin.plugins.download.direct

import org.gameyfin.pluginapi.core.wrapper.PluginContext
import org.gameyfin.pluginapi.data.GameyfinDataService
import org.gameyfin.pluginapi.download.FileDownload
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.test.*

class DirectDownloadTest {
    @TempDir lateinit var root: Path

    @Test
    fun `configured modes produce readable archives with correct size contracts`() {
        val plugin = DirectDownloadPlugin(object : PluginContext {
            override val pluginId = "direct"
            override val pluginVersion = "test"
            override val dataDirectory get() = root
            override val dataService: GameyfinDataService get() = error("unused")
        })
        val folder = Files.createDirectory(root.resolve("game"))
        val content = "game data ".repeat(100_000).toByteArray()
        Files.write(folder.resolve("café.txt"), content)
        Files.write(folder.resolve("empty"), byteArrayOf())
        val lengths = mutableMapOf<CompressionMode, Int>()
        for (mode in CompressionMode.entries) {
            plugin.loadConfig(mapOf("compressionMode" to mode.name))
            val download = DirectDownloadPlugin.DirectDownloadProvider().download(folder) as FileDownload
            val bytes = download.data.use { it.readAllBytes() }
            lengths[mode] = bytes.size
            if (mode == CompressionMode.None) assertEquals(bytes.size.toLong(), download.size)
            else assertNull(download.size)
            val zip = Files.write(root.resolve("$mode.zip"), bytes)
            ZipFile(zip.toFile()).use {
                val entry = it.getEntry("café.txt")
                assertEquals(if (mode == CompressionMode.None) 0 else 8, entry.method)
                assertContentEquals(content, it.getInputStream(entry).readAllBytes())
                assertEquals(0, it.getEntry("empty").size)
            }
        }
        assertTrue(lengths.getValue(CompressionMode.Fast) < lengths.getValue(CompressionMode.None))
        assertTrue(lengths.getValue(CompressionMode.Best) < lengths.getValue(CompressionMode.None))
    }

    @Test
    fun `file ranges seek and stop at requested length including large offsets`() {
        val path = root.resolve("large.bin")
        val offset = 5_000_000_000L
        java.io.RandomAccessFile(path.toFile(), "rw").use { it.seek(offset); it.write(byteArrayOf(7, 8, 9)) }
        FileRangeDownload(path).use { resource ->
            assertEquals(offset + 3, resource.metadata.size)
            resource.openStream(offset, 2).use { assertContentEquals(byteArrayOf(7, 8), it.readAllBytes()) }
        }
        // Windows also verifies that no open handle remains.
        Files.delete(path)
    }

    @Test
    fun `changing file invalidates validators and prepared streams`() {
        val path = Files.write(root.resolve("game.bin"), byteArrayOf(1))
        FileRangeDownload(path).use { original ->
            Files.write(path, byteArrayOf(1, 2))
            FileRangeDownload(path).use { assertNotEquals(original.etag, it.etag) }
            assertFailsWith<IOException> { original.openStream(0, 1) }
        }
    }

    @Test
    fun `producer failure reaches consumer even after emitted bytes`() {
        ArchiveStream.open { output ->
            output.write(ByteArray(600_000))
            throw IOException("source failed")
        }.use { input ->
            assertFailsWith<IOException> { input.readAllBytes() }
        }
    }

    @Test
    fun `closing archive cancels blocked producer`() {
        val stopped = CountDownLatch(1)
        val input = ArchiveStream.open { output ->
            try { while (true) output.write(ByteArray(64 * 1024)) }
            finally { stopped.countDown() }
        }
        input.read()
        input.close()
        assertTrue(stopped.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `stored archive fails when a later file changes after sizing`() {
        val folder = Files.createDirectory(root.resolve("changing"))
        Files.write(folder.resolve("a.bin"), ByteArray(2 * 1024 * 1024))
        val later = Files.write(folder.resolve("b.bin"), ByteArray(10))
        val download = StoredZip.download(folder)
        // The producer cannot reach b.bin before the pipe is drained.
        Files.write(later, ByteArray(20))
        download.data.use { assertFailsWith<IOException> { it.readAllBytes() } }
    }

    @Test
    fun `unreadable source does not become a successful tiny zip`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val folder = Files.createDirectory(root.resolve("locked"))
        val path = Files.write(folder.resolve("locked.bin"), ByteArray(1024))
        FileChannel.open(path, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use {
                for (mode in CompressionMode.entries) {
                    val input = if (mode == CompressionMode.None) StoredZip.stream(folder) else CompressedZip.stream(folder, mode)
                    input.use { assertFailsWith<IOException> { it.readAllBytes() } }
                }
            }
        }
    }
}
