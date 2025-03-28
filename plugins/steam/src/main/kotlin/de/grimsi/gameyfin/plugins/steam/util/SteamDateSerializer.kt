package de.grimsi.gameyfin.plugins.steam.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = Instant::class)
class SteamDateSerializer : KSerializer<Instant> {

    companion object {
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.ENGLISH)
    }

    override fun deserialize(decoder: Decoder): Instant = fromString(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    private fun fromString(dateString: String): Instant {
        val localDate = LocalDate.parse(dateString, formatter)
        return localDate.atStartOfDay().toInstant(ZoneOffset.UTC)
    }

    private fun toString(date: Instant): String {
        return formatter.format(date.atZone(ZoneOffset.UTC))
    }
}