package org.gameyfin.plugins.store.gogdb

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

private class FakeHttp(private val responses: Map<String, String>) : GogDbHttp {
    val requests = mutableListOf<String>()

    override fun getJson(uri: URI): JsonElement {
        requests += uri.toString()
        val body = responses.entries.firstOrNull { uri.toString().contains(it.key) }?.value
            ?: throw IllegalStateException("HTTP 404 for $uri")
        return json(body)
    }
}

class GogDbParsingTest {

    @Test
    fun `parses GOGDB archive builds and filters to Windows generation 2`() {
        val product = json(
            """
            {"builds": [
              {"id": 123, "version": "1.2.0", "os": "windows", "generation": 2, "branch": null, "date_published": "2024-12-22T02:35:35+00:00"},
              {"id": 456, "version": "1.1.0", "os": "osx", "generation": 2, "date_published": "2024-12-20T02:35:35+00:00"},
              {"id": 789, "version": "", "os": "windows", "generation": 1}
            ]}
            """.trimIndent()
        )

        val versions = parseGogDbProduct(product)

        assertEquals(1, versions.size)
        assertEquals("123", versions.single().buildId)
        assertEquals("1.2.0", versions.single().label)
        assertEquals("", versions.single().branch)
        assertEquals(Instant.parse("2024-12-22T02:35:35Z"), versions.single().releasedAt)
    }

    @Test
    fun `reads the games included in a GOG pack and tells games from DLC`() {
        val pack = json("""{"type": "pack", "includes_games": [1122806862, 1508702879]}""")

        assertEquals(listOf("1122806862", "1508702879"), parseGogDbIncludedGameIds(pack))
        assertEquals(false, isGogDbGame(pack))
        assertEquals(true, isGogDbGame(json("""{"type": "game"}""")))
        assertEquals(emptyList(), parseGogDbIncludedGameIds(json("""{"type": "game"}""")))
    }

    @Test
    fun `GOGDB edition names skip the standard edition`() {
        val product = json(
            """{"editions":[{"name":"Standard Edition"},{"name":"Base Edition"},{"name":"Galaxy Edition"},{"name":" Starter Edition "},{"name":"Galaxy Edition"},{"name":""}]}"""
        )

        assertEquals(listOf("Galaxy Edition", "Starter Edition"), parseGogDbEditions(product))
        assertTrue(parseGogDbEditions(json("""{}""")).isEmpty())
    }
}

class GogDbProviderTest {
    private val builds =
        """{"builds":[{"id":5,"version":"2.0","os":"windows","generation":2},{"id":6,"version":"2.1","os":"windows","generation":2,"date_published":"2024-12-22T02:35:35+00:00"}]}"""

    @Test
    fun `describes the GOG store and parses product URLs`() {
        val info = GogDbProvider(FakeHttp(emptyMap())).store

        assertEquals("gog", info.id)
        assertEquals("1453375253", info.parseProductId("https://www.gogdb.org/product/1453375253"))
        assertNull(info.parseProductId("1/other"))
    }

    @Test
    fun `provider rejects nonnumeric product IDs before making requests`() {
        val http = FakeHttp(emptyMap())

        assertFailsWith<IllegalArgumentException> { GogDbProvider(http).fetchVersions("1/other") }
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `fetches versions newest first`() {
        val http = FakeHttp(mapOf("gogdb.org/data/products/1/" to builds))

        assertEquals(listOf("6", "5"), GogDbProvider(http).fetchVersions("1").map { it.buildId })
    }

    @Test
    fun `packs use the versions of the included base game`() {
        val http = FakeHttp(
            mapOf(
                "gogdb.org/data/products/1/" to """{"type":"pack","includes_games":[2],"builds":[]}""",
                "gogdb.org/data/products/2/" to """{"type":"game","builds":[{"id":7,"version":"1.0","os":"windows","generation":2}]}"""
            )
        )

        assertEquals(listOf("7"), GogDbProvider(http).fetchVersions("1").map { it.buildId })
    }

    @Test
    fun `fails if neither the product nor an included game has versions`() {
        assertFailsWith<IllegalStateException> { GogDbProvider(FakeHttp(emptyMap())).fetchVersions("1") }
    }

    @Test
    fun `provides no search or DLC`() {
        val http = FakeHttp(emptyMap())
        val provider = GogDbProvider(http)

        assertTrue(provider.search("Hades").isEmpty())
        assertTrue(provider.fetchDlc("1").isEmpty())
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `edition suggestions are cached and failures give no suggestions`() {
        val http = FakeHttp(mapOf("gogdb.org/data/products/1/" to """{"editions":[{"name":"Galaxy Edition"}]}"""))
        val provider = GogDbProvider(http)

        assertEquals(listOf("Galaxy Edition"), provider.suggestEditions("1"))
        assertEquals(listOf("Galaxy Edition"), provider.suggestEditions("https://www.gogdb.org/product/1"))
        assertEquals(1, http.requests.size)
        assertTrue(provider.suggestEditions("9").isEmpty())
        assertTrue(provider.suggestEditions("bad id").isEmpty())
    }
}
