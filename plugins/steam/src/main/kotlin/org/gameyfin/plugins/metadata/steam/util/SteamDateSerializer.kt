package org.gameyfin.plugins.metadata.steam.util

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
        const val TO_BE_ANNOUNCED_TEXT = "To be announced"

        val FALLBACK_DATE: LocalDate = LocalDate.parse("2999-12-31")

        val formatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM, uuuu", Locale.ENGLISH)

        val steamFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH)
    }

    override fun serialize(encoder: Encoder, value: Instant?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): Instant? {
        return if (decoder.decodeNotNullMark()) {
            fromString(decoder.decodeString())
        } else {
            decoder.decodeNull()
            null
        }
    }

    fun deserialize(dateString: String): Instant? {
        return fromString(dateString)
    }

    private fun fromString(dateString: String): Instant? {
        val normalizedDateString = dateString
            .trim()
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")

        return try {
            if (normalizedDateString.isBlank()) {
                return null
            }

            if (
                normalizedDateString.equals(COMING_SOON_TEXT, true) ||
                normalizedDateString.equals(TO_BE_ANNOUNCED_TEXT, true)
            ) {
                return FALLBACK_DATE
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
            }

            val quarterMatch = Regex("""Q([1-4]) (\d{4})""").matchEntire(normalizedDateString)

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

            val yearMatch = Regex("""^(\d{4})$""").matchEntire(normalizedDateString)

            if (yearMatch != null) {
                val (yearStr) = yearMatch.destructured

                return LocalDate.of(yearStr.toInt(), 1, 1)
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
            }

            val localDate = try {
                LocalDate.parse(normalizedDateString, steamFormatter)
            } catch (_: Exception) {
                LocalDate.parse(normalizedDateString, formatter)
            }

            localDate
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)

        } catch (e: Exception) {
            log.warn("Couldn't parse date string: '$dateString' normalized='$normalizedDateString'", e)
            null
        }
    }
}