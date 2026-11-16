package org.gameyfin.plugins.metadata.steam.dto

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SteamGameDetailsTest {
    @Test
    fun `movie formats preserve numeric resolution keys in both directions`() {
        val webm = Json.decodeFromString<Webm>("""{"480":"low.webm","max":"full.webm"}""")
        val mp4 = Json.decodeFromString<Mp4>("""{"480":"low.mp4","max":"full.mp4"}""")

        assertEquals("low.webm", webm.resolution480)
        assertEquals("low.mp4", mp4.resolution480)
        assertEquals(
            Json.parseToJsonElement("""{"480":"low.webm","max":"full.webm"}"""),
            Json.parseToJsonElement(Json.encodeToString(webm))
        )
        assertEquals(
            Json.parseToJsonElement("""{"480":"low.mp4","max":"full.mp4"}"""),
            Json.parseToJsonElement(Json.encodeToString(mp4))
        )
    }

    @Test
    fun `missing optional resolutions retain their defaults`() {
        assertNull(Json.decodeFromString<Webm>("""{"max":"full.webm"}""").resolution480)
        assertEquals(Mp4(), Json.decodeFromString<Mp4>("{}"))
        assertEquals(Mp4(), Json.decodeFromString<Mp4>("""{"480":null,"max":null}"""))
        assertFailsWith<SerializationException> { Json.decodeFromString<Webm>("{}") }
    }
}
