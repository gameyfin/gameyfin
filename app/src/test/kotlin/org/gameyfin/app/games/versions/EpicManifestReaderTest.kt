package org.gameyfin.app.games.versions

import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpicManifestReaderTest {

    private fun fstring(value: String): ByteArray {
        val raw = (value + "\u0000").toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(4 + raw.size).order(ByteOrder.LITTLE_ENDIAN).putInt(raw.size).put(raw).array()
    }

    private fun utf16String(value: String): ByteArray {
        val raw = (value + "\u0000").toByteArray(Charsets.UTF_16LE)
        return ByteBuffer.allocate(4 + raw.size).order(ByteOrder.LITTLE_ENDIAN).putInt(-(raw.size / 2)).put(raw).array()
    }

    private fun metaBlock(buildVersion: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val fixed = ByteBuffer.allocate(4 + 1 + 4 + 1 + 4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0).put(21).putInt(18).put(0).putInt(0).array()
        out.write(fixed)
        out.write(fstring("AppName"))
        out.write(buildVersion)
        out.write(fstring("Game.exe"))
        return out.toByteArray()
    }

    private fun manifest(meta: ByteArray, compressed: Boolean): ByteArray {
        val body = if (compressed) {
            val deflater = Deflater()
            deflater.setInput(meta)
            deflater.finish()
            val buffer = ByteArray(meta.size + 64)
            val size = deflater.deflate(buffer)
            deflater.end()
            buffer.copyOf(size)
        } else meta

        val header = ByteBuffer.allocate(41).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x44BEC00C).putInt(41).putInt(meta.size).putInt(body.size)
            .put(ByteArray(20)).put(if (compressed) 1 else 0).putInt(18)
            .array()
        return header + body
    }

    @Test
    fun `reads build version from compressed manifest`() {
        val bytes = manifest(metaBlock(fstring("38294-x64")), compressed = true)

        assertEquals("38294-x64", EpicManifestReader.parseBuildVersion(bytes))
    }

    @Test
    fun `reads build version from uncompressed manifest with UTF-16 strings`() {
        val bytes = manifest(metaBlock(utf16String("1.2.3-é")), compressed = false)

        assertEquals("1.2.3-é", EpicManifestReader.parseBuildVersion(bytes))
    }

    @Test
    fun `returns null for invalid or truncated manifests`() {
        assertNull(EpicManifestReader.parseBuildVersion(ByteArray(0)))
        assertNull(EpicManifestReader.parseBuildVersion("not a manifest at all, just text".toByteArray()))
        val valid = manifest(metaBlock(fstring("1.0")), compressed = false)
        assertNull(EpicManifestReader.parseBuildVersion(valid.copyOf(45)))
    }
}
