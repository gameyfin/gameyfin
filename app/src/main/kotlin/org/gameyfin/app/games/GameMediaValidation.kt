package org.gameyfin.app.games

import org.apache.tika.Tika
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.HttpHeaders
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TIFF
import org.apache.tika.parser.ParseContext
import org.apache.tika.parser.image.ImageMetadataExtractor
import org.apache.tika.parser.image.ImageParser
import org.apache.tika.parser.image.JpegParser
import org.apache.tika.parser.image.WebPParser
import org.gameyfin.app.games.dto.GameImageSelectionDto
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile
import org.xml.sax.helpers.DefaultHandler
import java.net.URI

internal object GameMediaValidation {
    const val MAX_FILE_SIZE = 20L * 1024 * 1024
    const val MAX_REQUEST_SIZE = 100L * 1024 * 1024
    private const val IMAGE_WEBP_VALUE = "image/webp"
    private val tika = Tika()
    private val imageParser = ImageParser()
    private val parsers = mapOf(
        MediaType.IMAGE_JPEG_VALUE to JpegParser(),
        MediaType.IMAGE_PNG_VALUE to imageParser,
        IMAGE_WEBP_VALUE to WebPParser(),
        MediaType.IMAGE_GIF_VALUE to imageParser
    )

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
        val mime = file.inputStream.use { tika.detect(it) }
        val parser = requireNotNull(parsers[mime]) { "Supported image formats: JPEG, PNG, WebP, GIF" }
        val metadata = Metadata().apply { set(HttpHeaders.CONTENT_TYPE, mime) }
        try {
            TikaInputStream.get(file.inputStream).use { input ->
                parser.parse(input, DefaultHandler(), metadata, ParseContext())
            }
        } catch (exception: Exception) {
            throw IllegalArgumentException("The uploaded file is not a valid image", exception)
        }
        // These parsers read headers and metadata, rather than fully decoding pixel data.
        // WebP dimensions are exposed as native tags instead of the standard TIFF properties.
        val width = if (mime == IMAGE_WEBP_VALUE) {
            metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Image Width")?.toIntOrNull()
        } else metadata.getInt(TIFF.IMAGE_WIDTH)
        val height = if (mime == IMAGE_WEBP_VALUE) {
            metadata.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Image Height")?.toIntOrNull()
        } else metadata.getInt(TIFF.IMAGE_LENGTH)
        require(width != null && width > 0 && height != null && height > 0) {
            "The uploaded file is not a valid image"
        }
        return mime
    }
}
