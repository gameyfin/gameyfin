package org.gameyfin.plugins.metadata.steam.util

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SteamDateSerializerTest {
    private val serializer = SteamDateSerializer()

    @Test
    fun `parses both Steam date orders and normalizes whitespace`() {
        val expected = Instant.parse("2025-03-07T00:00:00Z")
        for (date in listOf("Mar 7, 2025", "7 Mar, 2025", "  Mar\u00A0 7,\t2025  ")) {
            assertEquals(expected, serializer.deserialize(date), date)
        }
    }

    @Test
    fun `preserves year quarter and coming soon formats`() {
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), serializer.deserialize(" 2025 "))
        assertEquals(Instant.parse("2025-07-01T00:00:00Z"), serializer.deserialize("Q3  2025"))
        for (date in listOf("Coming Soon", "coming soon", " To be announced ")) {
            assertEquals(Instant.parse("2999-12-31T00:00:00Z"), serializer.deserialize(date))
        }
    }

    @Test
    fun `returns null for blank and unsupported dates`() {
        for (date in listOf("", " \u00A0 ", "not a date", "Q5 2025")) {
            assertNull(serializer.deserialize(date), date)
        }
    }
}
