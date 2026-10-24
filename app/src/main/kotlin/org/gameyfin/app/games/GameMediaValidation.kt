package org.gameyfin.app.games

import org.apache.tika.Tika
import org.gameyfin.app.games.dto.GameImageSelectionDto
import org.springframework.web.multipart.MultipartFile
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

internal object GameMediaValidation {
    const val MAX_FILE_SIZE = 20L * 1024 * 1024
    const val MAX_REQUEST_SIZE = 100L * 1024 * 1024
    private val types = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

    fun httpUrl(value: String): URI {
        val uri = URI.create(value)
        require(uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()) {
            "Media URLs must be absolute HTTP(S) URLs"
        }
        return uri
    }

    fun selection(selection: GameImageSelectionDto, allowedIds: Set<Long>, uploadKeys: Set<String>) {
        require(listOf(selection.id, selection.url, selection.uploadKey).count { it != null } == 1) {
            "Select exactly one image ID, URL, or upload"
        }
        selection.id?.let { require(it in allowedIds) { "Image does not belong to this game and media role" } }
        selection.url?.let { httpUrl(it) }
        selection.uploadKey?.let { require(it in uploadKeys) { "Missing uploaded image: $it" } }
    }

    fun upload(file: MultipartFile): String {
        require(file.size in 1..MAX_FILE_SIZE) { "Images must be between 1 byte and 20 MiB" }
        val mime = file.inputStream.use { Tika().detect(it) }
        require(mime in types) { "Supported image formats: JPEG, PNG, WebP, GIF" }
        val valid = runCatching {
            if (mime == "image/webp") {
                validWebP(file.bytes)
            } else {
                file.inputStream.use { input ->
                    ImageIO.read(input)?.let { image ->
                        val validDimensions = image.width > 0 && image.height > 0
                        image.flush()
                        validDimensions
                    } ?: false
                }
            }
        }.getOrDefault(false)
        require(valid) { "The uploaded file is not a valid image" }
        return mime
    }

    private fun validWebP(bytes: ByteArray): Boolean {
        // The JDK has no WebP decoder. Check the complete RIFF structure and require a frame header,
        // including frames nested in animated WebP, rather than accepting a MIME signature alone.
        return bytes.size >= 25 && WebPValidator(bytes).isValid()
    }

    private class WebPValidator(private val bytes: ByteArray) {
        private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        private val frameTypes = setOf("VP8 ", "VP8L", "ANMF")

        private fun tag(offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)

        fun isValid(): Boolean {
            return !(tag(0) != "RIFF"
                    || buffer.getInt(4).toLong() + 8 != bytes.size.toLong()
                    || tag(8) != "WEBP") && frames(12, bytes.size)
        }

        private fun frames(start: Int, end: Int, nested: Boolean = false): Boolean {
            var offset = start
            var found = false
            while (offset + 8 <= end) {
                val size = buffer.getInt(offset + 4)
                if (size < 0 || offset.toLong() + 8 + size + (size and 1) > end) return false
                val data = offset + 8
                val type = tag(offset)
                if (!validChunk(type, data, size, nested)) return false
                if (type in frameTypes) found = true
                offset = data + size + (size and 1)
            }
            return offset == end && found
        }

        private fun validChunk(type: String, data: Int, size: Int, nested: Boolean): Boolean = when (type) {
            "VP8 " -> validLossyFrame(data, size)
            "VP8L" -> size >= 5 && bytes[data] == 0x2f.toByte()
            "ANMF" -> !nested && size >= 16 && frames(data + 16, data + size, true)
            else -> true
        }

        private fun validLossyFrame(data: Int, size: Int): Boolean =
            size >= 10 && bytes[data + 3] == 0x9d.toByte() && bytes[data + 4] == 0x01.toByte() &&
                    bytes[data + 5] == 0x2a.toByte() && buffer.getShort(data + 6).toInt() and 0x3fff != 0 &&
                    buffer.getShort(data + 8).toInt() and 0x3fff != 0
    }
}
