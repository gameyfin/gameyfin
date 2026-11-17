package org.gameyfin.app.games.versions

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.Inflater

/**
 * Reads the build version from the binary manifests the Epic Games Launcher stores in the `.egstore` folder
 * of an installed game. Only the metadata block at the start of the manifest is read.
 */
internal object EpicManifestReader {
    private const val MAGIC = 0x44BEC00C
    private const val MAX_MANIFEST_BYTES = 64L * 1024 * 1024
    private const val MAX_META_BYTES = 64 * 1024

    /** Returns the build versions of all manifests in [egstoreDirectory] that could be parsed. */
    fun readBuildVersions(egstoreDirectory: Path): List<String> {
        val manifests = Files.newDirectoryStream(egstoreDirectory, "*.manifest").use { it.toList() }
        return manifests.sorted().mapNotNull { manifest ->
            if (Files.size(manifest) > MAX_MANIFEST_BYTES) return@mapNotNull null
            parseBuildVersion(Files.readAllBytes(manifest))
        }
    }

    fun parseBuildVersion(bytes: ByteArray): String? = try {
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (header.getInt() != MAGIC) null
        else {
            val headerSize = header.getInt()
            val sizeUncompressed = header.getInt()
            val sizeCompressed = header.getInt()
            header.position(header.position() + 20) // SHA-1 of the body
            val compressed = header.get().toInt() and 1 != 0

            val body = bytes.copyOfRange(headerSize, headerSize + if (compressed) sizeCompressed else sizeUncompressed)
            val meta = ByteBuffer.wrap(if (compressed) inflate(body, sizeUncompressed) else body)
                .order(ByteOrder.LITTLE_ENDIAN)

            meta.getInt() // size of the meta block
            meta.get() // data version
            meta.getInt() // feature level
            meta.get() // is file data
            meta.getInt() // app ID
            readString(meta) // app name
            readString(meta).trim().takeIf { it.isNotEmpty() } // build version
        }
    } catch (_: RuntimeException) {
        null
    }

    private fun inflate(data: ByteArray, expectedSize: Int): ByteArray {
        // Only the start of the body is needed, so a corrupt or huge manifest cannot exhaust memory
        val limit = minOf(expectedSize, MAX_META_BYTES)
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArray(limit)
            var total = 0
            while (total < limit && !inflater.finished()) {
                val read = inflater.inflate(out, total, limit - total)
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                total += read
            }
            return out.copyOf(total)
        } finally {
            inflater.end()
        }
    }

    /** Unreal engine string: a signed length followed by UTF-8 (positive) or UTF-16LE (negative) with terminator. */
    private fun readString(buffer: ByteBuffer): String {
        val length = buffer.getInt()
        if (length == 0) return ""
        require(length in -4096..4096) { "Unreasonable string length" }
        return if (length > 0) {
            val raw = ByteArray(length).also(buffer::get)
            String(raw, 0, length - 1, StandardCharsets.UTF_8)
        } else {
            val chars = -length
            val raw = ByteArray(chars * 2).also(buffer::get)
            String(raw, 0, (chars - 1) * 2, StandardCharsets.UTF_16LE)
        }
    }
}
