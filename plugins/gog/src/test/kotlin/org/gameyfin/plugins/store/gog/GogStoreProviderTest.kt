package org.gameyfin.plugins.store.gog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.gameyfin.pluginapi.gamestore.GameStoreDlc
import org.gameyfin.pluginapi.gamestore.GameStoreDlcKind
import org.gameyfin.pluginapi.gamestore.GameStoreProduct
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

private class FakeHttp(private val responses: Map<String, String>) : GogHttp {
    val requests = mutableListOf<String>()

    override fun getJson(uri: URI): JsonElement {
        requests += uri.toString()
        val body = responses.entries.firstOrNull { uri.toString().contains(it.key) }?.value
            ?: throw IllegalStateException("HTTP 404 for $uri")
        return json(body)
    }
}

class GogParsingTest {

    @Test
    fun `parses GOG versions and retains branch and release date`() {
        val page = json(
            """
            {"total_count": 2, "items": [
              {"build_id": "build-2", "version_name": "2.0b", "branch": null, "date_published": "2024-12-22T02:35:35+0000"},
              {"build_id": "build-1", "version_name": "COMPAT-1.9", "branch": "compatibility", "date_published": "2024-12-20T02:35:35+0000"}
            ]}
            """.trimIndent()
        )

        val result = parseGogBuildPage(page)

        assertEquals(2, result.totalCount)
        assertEquals(2, result.itemCount)
        assertEquals("", result.versions[0].branch)
        assertEquals("2.0b", result.versions[0].label)
        assertEquals(Instant.parse("2024-12-22T02:35:35Z"), result.versions[0].releasedAt)
        assertEquals("compatibility", result.versions[1].branch)
    }

    @Test
    fun `rejects incomplete GOG response`() {
        assertFailsWith<IllegalStateException> { parseGogBuildPage(json("""{"items": []}""")) }
    }

    @Test
    fun `parses GOG DLC and drops invalid entries`() {
        val root = json(
            """{"expanded_dlcs":[{"id":2,"title":"B Pack"},{"id":1,"title":"A Pack"},{"id":"x","title":"Bad"},{"id":3,"title":" "},{"id":1,"title":"A Pack"}]}"""
        )

        assertEquals(
            listOf(GameStoreDlc("1", "A Pack"), GameStoreDlc("2", "B Pack")),
            parseGogDlc(root)
        )
        assertTrue(parseGogDlc(json("""{"expanded_dlcs":[]}""")).isEmpty())
        assertTrue(parseGogDlc(json("""{}""")).isEmpty())
    }

    @Test
    fun `GOG DLC is typed by title`() {
        val root = json(
            """{"expanded_dlcs":[{"id":1,"title":"Game Soundtrack"},{"id":2,"title":"Game Deluxe Edition Upgrade"},{"id":3,"title":"Story Pack"}]}"""
        )

        assertEquals(
            listOf(GameStoreDlcKind.EDITION_UPGRADE, GameStoreDlcKind.BONUS, GameStoreDlcKind.DLC),
            parseGogDlc(root).map { it.kind }
        )
        assertEquals(GameStoreDlcKind.BONUS, classifyExtra("Digital Artbook"))
        assertEquals(GameStoreDlcKind.BONUS, classifyExtra("Soundtrack Deluxe Edition"))
    }

    @Test
    fun `parses GOG search results with release year`() {
        val results = parseGogSearch(
            json(
                """{"products":[
                    {"id":"1453375253","title":"Stardew Valley","releaseDate":"2016.02.26","coverVertical":"https://example.test/c.jpg"},
                    {"id":"abc","title":"Invalid"}
                ]}"""
            )
        )

        assertEquals(listOf(GameStoreProduct("1453375253", "Stardew Valley", "2016", "https://example.test/c.jpg")), results)
        assertNull(parseGogSearch(json("""{"products":[{"id":"1","title":"X"}]}""")).single().subtitle)
        assertTrue(parseGogSearch(json("{}")).isEmpty())
    }

    @Test
    fun `title filter drops loosely related fuzzy matches`() {
        val results = listOf("Ova Magica", "Hades", "Hades II", "Outbreak Shades of Horror", "Curse of Pirates")
            .mapIndexed { index, title -> GameStoreProduct("$index", title, null, null) }

        assertEquals(listOf("Hades", "Hades II"), filterByTitleRelevance("Hades", results).map { it.title })
        assertTrue(filterByTitleRelevance("Stardew Valley", results).isEmpty())
        assertEquals(listOf("Hades II"), filterByTitleRelevance("hades: ii", results).map { it.title })
    }

    @Test
    fun `title filter treats roman numerals and digits alike`() {
        val results = listOf("The Witcher 3: Wild Hunt", "Paper Sorcerer")
            .mapIndexed { index, title -> GameStoreProduct("$index", title, null, null) }

        assertEquals(listOf("The Witcher 3: Wild Hunt"), filterByTitleRelevance("Witcher III", results).map { it.title })
    }

    @Test
    fun `search queries lose punctuation but keep apostrophes`() {
        assertEquals("The Witcher 3 Wild Hunt Remastered", normalizeSearchQuery("The Witcher 3: Wild Hunt — Remastered"))
        assertEquals("Assassin's Creed", normalizeSearchQuery("  Assassin's Creed! "))
    }
}

class GogStoreProviderTest {
    private val officialBuilds =
        """{"total_count":1,"items":[{"build_id":"b1","version_name":"1.0","branch":null,"date_published":"2024-12-22T02:35:35+0000"}]}"""

    @Test
    fun `describes the store and parses product URLs`() {
        val info = GogStoreProvider(FakeHttp(emptyMap())).store

        assertEquals("gog", info.id)
        assertEquals("1453375253", info.parseProductId("https://www.gogdb.org/product/1453375253"))
        assertNull(info.parseProductId("1/other"))
    }

    @Test
    fun `provider rejects nonnumeric product IDs before making requests`() {
        val http = FakeHttp(emptyMap())
        val provider = GogStoreProvider(http)

        assertFailsWith<IllegalArgumentException> { provider.fetchVersions("1/other") }
        assertFailsWith<IllegalArgumentException> { provider.fetchDlc("1/other") }
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `uses the official endpoint first`() {
        val http = FakeHttp(mapOf("content-system.gog.com/products/1/" to officialBuilds))

        val versions = GogStoreProvider(http).fetchVersions("1")

        assertEquals(listOf("b1"), versions.map { it.buildId })
        assertEquals(1, http.requests.size)
    }

    @Test
    fun `fails instead of returning partial builds`() {
        val http = FakeHttp(mapOf("content-system.gog.com/products/1/" to """{"total_count":5,"items":[]}"""))

        assertFailsWith<IllegalStateException> { GogStoreProvider(http).fetchVersions("1") }
    }

    @Test
    fun `no builds gives an empty list`() {
        val http = FakeHttp(mapOf("content-system.gog.com/products/1/" to """{"total_count":0,"items":[]}"""))

        assertTrue(GogStoreProvider(http).fetchVersions("1").isEmpty())
    }

    @Test
    fun `fails if the official endpoint fails`() {
        assertFailsWith<IllegalStateException> { GogStoreProvider(FakeHttp(emptyMap())).fetchVersions("1") }
    }

    @Test
    fun `fetches DLC from the official API`() {
        val http = FakeHttp(mapOf("api.gog.com/products/1?expand=expanded_dlcs" to """{"expanded_dlcs":[{"id":2,"title":"Map Pack"}]}"""))

        assertEquals(listOf("2"), GogStoreProvider(http).fetchDlc("1").map { it.id })
    }

    @Test
    fun `search ignores short queries and filters fuzzy results`() {
        val http = FakeHttp(
            mapOf(
                "catalog.gog.com" to """{"products":[{"id":"1","title":"Hades","releaseDate":"2020.09.17"},{"id":"2","title":"Unrelated"}]}"""
            )
        )
        val provider = GogStoreProvider(http)

        assertTrue(provider.search(" a ").isEmpty())
        assertTrue(http.requests.isEmpty())
        assertEquals(listOf("1"), provider.search("Hades!").map { it.id })
        assertTrue(http.requests.single().contains("like%3AHades"))
    }
}
