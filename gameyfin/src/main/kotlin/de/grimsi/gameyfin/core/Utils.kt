package de.grimsi.gameyfin.core

import org.apache.tika.Tika
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.io.InputStream


class Utils {
    companion object {
        private val tika = Tika()

        fun maskEmail(email: String): String {
            val regex = """(?:\G(?!^)|(?<=^[^@]{2}|@))[^@](?!\.[^.]+$)""".toRegex()
            return email.replace(regex, "*")
        }

        fun getBaseUrl(): String {
            val request = (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).request
            val scheme = request.scheme
            val serverName = request.serverName
            val serverPort = request.serverPort

            return if (serverPort == 80 || serverPort == 443) {
                "$scheme://$serverName"
            } else {
                "$scheme://$serverName:$serverPort"
            }
        }

        fun inputStreamToResponseEntity(stream: InputStream?): ResponseEntity<InputStreamResource> {
            if (stream == null) return ResponseEntity.notFound().build()

            val inputStreamResource = InputStreamResource(stream)

            val headers = HttpHeaders()
            val contentLength = stream.available().toLong()
            val contentType = tika.detect(stream)

            headers.contentLength = contentLength
            headers.contentType = MediaType.parseMediaType(contentType)

            return ResponseEntity.ok()
                .headers(headers)
                .body(inputStreamResource)
        }
    }
}

/**
 * Converts a map with nullable values to a map with non-nullable values by filtering out null values
 */
@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V?>.filterValuesNotNull() = filterValues { it != null } as Map<K, V>

/**
 * Converts a string to an alphanumeric string by removing all non-alphanumeric characters (except whitespaces)
 * and converting it to lowercase
 */
fun String.alphaNumeric() = filter { it.isLetterOrDigit() || it.isWhitespace() }.lowercase()