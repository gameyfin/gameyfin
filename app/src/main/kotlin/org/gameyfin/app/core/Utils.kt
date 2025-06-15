package org.gameyfin.app.core

import org.apache.tika.Tika
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import kotlin.text.iterator


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

        fun inputStreamToResponseEntity(bytes: ByteArray?): ResponseEntity<ByteArrayResource> {
            if (bytes == null) return ResponseEntity.notFound().build()

            val byteArrayResource = ByteArrayResource(bytes)

            val headers = HttpHeaders()
            val contentLength = bytes.size.toLong()
            val contentType = tika.detect(bytes)

            headers.contentLength = contentLength
            headers.contentType = MediaType.parseMediaType(contentType)

            return ResponseEntity.ok()
                .headers(headers)
                .body(byteArrayResource)
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

/**
 * Replaces standalone Roman numerals in a string with their corresponding integer values.
 *
 * Roman numerals are detected only when they appear as separate "words"—
 * i.e., they are preceded by whitespace or the start of the string,
 * and followed by a word boundary, whitespace, or the end of the string.
 *
 * Valid Roman numerals are assumed to be in the range 1 to 3999 (inclusive),
 * in line with standard Roman numeral notation. Any match outside this range
 * is ignored and left unchanged to avoid false positives.
 *
 * Example:
 *   "Helldivers II"       -> "Helldivers 2"
 *   "Age of Empires III"  -> "Age of Empires 3"
 *   "IVy League"          -> "IVy League" (unchanged)
 */
fun String.replaceRomanNumerals(): String {
    val romanNumeralMap = mapOf(
        'M' to 1000, 'D' to 500, 'C' to 100,
        'L' to 50, 'X' to 10, 'V' to 5, 'I' to 1
    )

    fun romanToInt(roman: String): Int {
        var sum = 0
        var prev = 0
        for (char in roman.reversed()) {
            val value = romanNumeralMap[char] ?: return -1
            if (value < prev) sum -= value else sum += value
            prev = value
        }
        return sum
    }

    val regex = Regex(
        """(?<=\s|^)(M{0,4}(CM|CD|D?C{0,3})?(XC|XL|L?X{0,3})?(IX|IV|V?I{0,3})?)(?=\b|\s|$)""",
        RegexOption.IGNORE_CASE
    )

    return regex.replace(this) { match ->
        val roman = match.value.uppercase()
        val number = romanToInt(roman)
        if (number in 1..3999) number.toString() else match.value
    }
}
