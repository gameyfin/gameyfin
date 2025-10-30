package org.gameyfin.app.core

import jakarta.servlet.http.HttpServletRequest
import org.apache.tika.Tika
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Instant
import kotlin.time.Duration.Companion.nanoseconds


class Utils {

    companion object {
        private val tika = Tika()

        val jvmNanoTimeDiff: Long = System.currentTimeMillis() * 1_000_000 - System.nanoTime()

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


/**
 * Get the remote IP address, preferring IPv4 over IPv6.
 * Checks X-Forwarded-For header first (for proxied requests), then falls back to remoteAddr.
 *
 * @param lookupPolicy The policy to use when selecting the IP address
 * @return The remote IP address as a string, or "unknown" if none found
 */
fun HttpServletRequest.getRemoteIp(lookupPolicy: LookupPolicy = LookupPolicy.ANY): String {
    val candidateIps = mutableSetOf<String>()

    // Check X-Forwarded-For header (for requests behind proxies/load balancers)
    this.getHeader("X-Forwarded-For")?.let { forwardedFor ->
        // X-Forwarded-For can contain multiple IPs: "client, proxy1, proxy2"
        candidateIps.addAll(forwardedFor.split(",").map { it.trim() })
    }

    // Add the direct remote address
    this.remoteAddr?.let { candidateIps.add(it) }

    when (lookupPolicy) {
        LookupPolicy.IPV4_ONLY -> {
            val ipv4Address = candidateIps.firstOrNull { isIpv4(it) }
            return ipv4Address ?: "unknown"
        }

        LookupPolicy.IPV6_ONLY -> {
            val ipv6Address = candidateIps.firstOrNull { isIpv6(it) }
            return ipv6Address ?: "unknown"
        }

        LookupPolicy.IPV4_PREFERRED -> {
            val ipv4Address = candidateIps.firstOrNull { isIpv4(it) }
            return ipv4Address ?: run {
                val ipv6Address = candidateIps.firstOrNull { isIpv6(it) }
                ipv6Address ?: "unknown"
            }
        }

        LookupPolicy.IPV6_PREFERRED -> {
            val ipv6Address = candidateIps.firstOrNull { isIpv6(it) }
            return ipv6Address ?: run {
                val ipv4Address = candidateIps.firstOrNull { isIpv4(it) }
                ipv4Address ?: "unknown"
            }
        }

        LookupPolicy.ANY -> {
            return candidateIps.firstOrNull() ?: "unknown"
        }
    }
}

/**
 * Policy for looking up IP addresses
 */
enum class LookupPolicy {
    IPV4_PREFERRED,
    IPV6_PREFERRED,
    IPV4_ONLY,
    IPV6_ONLY,
    ANY
}

/**
 * Check if an IP address is IPv4 format
 */
fun isIpv4(ip: String): Boolean {
    return ip.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$"""))
}

/**
 * Check if an IP address is IPv6 format
 */
fun isIpv6(ip: String): Boolean {
    return ip.contains(":")
}

/**
 * Convert a nanoTime value to an Instant, adjusting for JVM start time
 */
fun nanoTimeToInstant(nanoTime: Long): Instant {
    val nanoNow = nanoTime + Utils.jvmNanoTimeDiff
    return Instant.ofEpochSecond(nanoNow.nanoseconds.inWholeSeconds)
}