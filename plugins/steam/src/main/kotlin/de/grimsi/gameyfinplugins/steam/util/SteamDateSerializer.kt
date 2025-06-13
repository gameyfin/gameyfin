package de.grimsi.gameyfinplugins.steam.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = Instant::class)
class SteamDateSerializer : KSerializer<Instant?> {

    companion object {
        val log: Logger = LoggerFactory.getLogger(SteamDateSerializer::class.java)

        const val COMING_SOON_TEXT = "Coming Soon"
        val COMING_SOON_FALLBACK_DATE: LocalDate = LocalDate.parse("2999-12-31")

        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.ENGLISH)
    }

    override fun deserialize(decoder: Decoder): Instant? = fromString(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Instant?) = encoder.encodeString(value.toString())

    private fun fromString(dateString: String): Instant? {
        return try {
            // Return null for empty strings
            if (dateString.isBlank()) {
                return null
            }

            // Match "Coming Soon" and return a fallback date
            if (dateString.equals(COMING_SOON_TEXT, true)) {
                return COMING_SOON_FALLBACK_DATE.atStartOfDay().toInstant(ZoneOffset.UTC)
            }

            // Match quarters like "Q1 2023", "Q2 2023", etc.
            val quarterMatch = Regex("""Q([1-4]) (\d{4})""").matchEntire(dateString)
            if (quarterMatch != null) {
                val (qStr, yearStr) = quarterMatch.destructured
                val month = when (qStr.toInt()) {
                    1 -> 1
                    2 -> 4
                    3 -> 7
                    4 -> 10
                    else -> 1
                }
                return LocalDate.of(yearStr.toInt(), month, 1)
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
            }

            // Match year only
            val yearMatch = Regex("""^(\d{4})$""").matchEntire(dateString)
            if (yearMatch != null) {
                val (yearStr) = yearMatch.destructured
                return LocalDate.of(yearStr.toInt(), 1, 1)
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
            }

            val localDate = LocalDate.parse(dateString, formatter)
            return localDate.atStartOfDay().toInstant(ZoneOffset.UTC)
        } catch (_: Exception) {
            log.warn("Couldn't parse date string: '$dateString'")
            null // Return null if parsing fails
        }
    }
}