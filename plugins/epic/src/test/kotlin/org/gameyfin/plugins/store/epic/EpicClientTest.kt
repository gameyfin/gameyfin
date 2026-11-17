package org.gameyfin.plugins.store.epic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gameyfin.pluginapi.gamestore.GameStoreDlc
import org.gameyfin.pluginapi.gamestore.GameStoreDlcKind
import org.gameyfin.pluginapi.gamestore.GameStoreProduct
import java.net.URI
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpicClientTest {

    private fun json(text: String) = Json.parseToJsonElement(text)

    private class FakeHttp(private val response: JsonElement) : EpicHttp {
        val requests = mutableListOf<String>()
        var postBody: String? = null

        override fun getJson(uri: URI): JsonElement = response.also { requests += "GET $uri" }

        override fun postJson(uri: URI, body: String): JsonElement = response.also {
            requests += "POST $uri"
            postBody = body
        }

        override fun close() {}
    }

    @Test
    fun `parses Epic builds using Windows live label and earliest sighting`() {
        val builds = json(
            """
            [
              {"appName":"Min","buildVersion":"38294-x64","labelName":"Live-Windows","platform":"Windows","firstSeenAt":"2025-12-15T10:00:00.000Z"},
              {"appName":"Min","buildVersion":"38294-x64","labelName":"Live-Windows","platform":"Windows","firstSeenAt":"2025-07-23T08:30:00.000Z"},
              {"appName":"Min","buildVersion":"30000-x64","labelName":"Live-Windows","platform":"Windows","firstSeenAt":"2025-01-02T00:00:00.000Z"},
              {"appName":"Test","buildVersion":"99999-x64","labelName":"-Windows","platform":"Windows","firstSeenAt":"2026-04-19T09:00:00.000Z"},
              {"appName":"Min","buildVersion":"1.0-mac","labelName":"Live-Mac","platform":"Mac","firstSeenAt":"2025-01-01T00:00:00.000Z"}
            ]
            """.trimIndent()
        )

        val versions = parseEpicBuilds(builds)

        assertEquals(listOf("38294-x64", "30000-x64"), versions.map { it.buildId })
        assertEquals("", versions.first().branch)
        assertEquals("38294-x64 (2025-07-23)", versions.first().label)
        assertEquals(Instant.parse("2025-07-23T08:30:00Z"), versions.first().releasedAt)
    }

    @Test
    fun `Epic falls back to other Windows builds when there is no live label`() {
        val versions = parseEpicBuilds(
            json("""[{"buildVersion":"7","labelName":"Beta-Windows","platform":"Windows","firstSeenAt":"2025-01-02T00:00:00Z"}]""")
        )

        assertEquals("Beta", versions.single().branch)
        assertEquals("Beta: 7 (2025-01-02)", versions.single().label)
    }

    @Test
    fun `non array build response is rejected`() {
        assertFailsWith<IllegalStateException> { parseEpicBuilds(json("{}")) }
    }

    @Test
    fun `client rejects invalid offer IDs before making requests`() {
        val http = FakeHttp(json("[]"))
        val client = EpicClient(http)

        assertFailsWith<IllegalArgumentException> { client.fetchVersions("../other") }
        assertFailsWith<IllegalArgumentException> { client.fetchVersions("123") }
        assertFailsWith<IllegalArgumentException> { client.fetchDlc("123") }
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `client requests builds and related offers of the offer`() {
        val offer = "a".repeat(32)
        val http = FakeHttp(json("[]"))
        val client = EpicClient(http)

        client.fetchVersions(offer)
        client.fetchDlc(offer)

        assertEquals(
            listOf("GET https://api.egdata.app/offers/$offer/builds", "GET https://api.egdata.app/offers/$offer/related"),
            http.requests
        )
    }

    @Test
    fun `store IDs are parsed from plain IDs and egdata URLs`() {
        val offer = "2ae6edb2223c4f8c97e9839b5b6497bb"
        assertEquals(offer, EPIC_STORE.parseProductId("https://egdata.app/offers/${offer.uppercase()}"))
        assertEquals(offer, EPIC_STORE.parseProductId(" ${offer.uppercase()} "))
        assertNull(EPIC_STORE.parseProductId("413150"))
        assertEquals("epic", EPIC_STORE.id)
        assertEquals("Epic Games", EPIC_STORE.displayName)
    }

    @Test
    fun `Epic lists DLC, add-ons and editions as extras but skips tools and internal entries`() {
        val own = "a".repeat(32)
        val root = json(
            """
            [
              {"id":"${"b".repeat(32)}","title":"Map Pack","offerType":"ADD_ON"},
              {"id":"${"c".repeat(32)}","title":"Story DLC","offerType":"DLC"},
              {"id":"${"d".repeat(32)}","title":"Ultimate Edition","offerType":"EDITION"},
              {"id":"${"e".repeat(32)}","title":"Keys","offerType":"OTHERS"},
              {"id":"${"f".repeat(32)}","title":"Ginger EP1 Staging","offerType":"DLC"},
              {"id":"${"1".repeat(32)}","title":"Original Soundtrack","offerType":"DLC"},
              {"id":"$own","title":"Itself","offerType":"DLC"}
            ]
            """.trimIndent()
        )

        assertEquals(
            listOf(
                GameStoreDlc("b".repeat(32), "Map Pack", GameStoreDlcKind.DLC),
                GameStoreDlc("1".repeat(32), "Original Soundtrack", GameStoreDlcKind.BONUS),
                GameStoreDlc("c".repeat(32), "Story DLC", GameStoreDlcKind.DLC),
                GameStoreDlc("d".repeat(32), "Ultimate Edition", GameStoreDlcKind.EDITION_UPGRADE)
            ),
            parseEpicDlc(root, own)
        )
    }

    @Test
    fun `extras are typed from store hints first and title words second`() {
        assertEquals(GameStoreDlcKind.BONUS, classifyExtra("Some Game", isBonus = true))
        assertEquals(GameStoreDlcKind.BONUS, classifyExtra("The Witcher 3: Wild Hunt - Hearts of Stone Soundtrack"))
        assertEquals(GameStoreDlcKind.BONUS, classifyExtra("Digital Artbook"))
        assertEquals(GameStoreDlcKind.BONUS, classifyExtra("Soundtrack Deluxe Edition"))
        assertEquals(GameStoreDlcKind.EDITION_UPGRADE, classifyExtra("Skyrim Anniversary Upgrade"))
        assertEquals(GameStoreDlcKind.EDITION_UPGRADE, classifyExtra("Plain title", isEdition = true))
        assertEquals(GameStoreDlcKind.DLC, classifyExtra("Phantom Liberty"))
    }

    @Test
    fun `parses Epic search results preferring the tall offer image`() {
        val results = parseEpicSearch(
            json(
                """{"offers":[
                    {"id":"2AE6EDB2223C4F8C97E9839B5B6497BB","title":"Hades","seller":{"name":"Supergiant Games"},
                     "keyImages":[{"type":"Thumbnail","url":"https://example.test/t.jpg"},{"type":"OfferImageTall","url":"https://example.test/tall.jpg"}]},
                    {"id":"short","title":"Invalid"}
                ]}"""
            )
        )

        val hades = results.single()
        assertEquals("2ae6edb2223c4f8c97e9839b5b6497bb", hades.id)
        assertEquals("Supergiant Games", hades.subtitle)
        assertEquals("https://example.test/tall.jpg", hades.imageUrl)
    }

    @Test
    fun `search ignores short queries and posts a normalized base game query`() {
        val http = FakeHttp(
            json("""{"offers":[{"id":"${"a".repeat(32)}","title":"Hades"},{"id":"${"b".repeat(32)}","title":"Stardew Valley"}]}""")
        )
        val client = EpicClient(http)

        assertTrue(client.search("a").isEmpty())
        assertTrue(http.requests.isEmpty())

        val results = client.search("Hades!")
        assertEquals(listOf("Hades"), results.map { it.title })
        assertEquals(listOf("POST https://api.egdata.app/search/v2/search"), http.requests)
        val body = Json.parseToJsonElement(http.postBody!!).jsonObject
        assertEquals("Hades", body["title"]!!.jsonPrimitive.content)
        assertEquals("BASE_GAME", body["offerType"]!!.jsonPrimitive.content)
        assertEquals("10", body["limit"]!!.jsonPrimitive.content)
    }

    @Test
    fun `search results are filtered by title relevance`() {
        val results = listOf(
            GameStoreProduct("1", "Hades"),
            GameStoreProduct("2", "Hades II"),
            GameStoreProduct("3", "Stardew Valley")
        )
        assertEquals(listOf("Hades", "Hades II"), filterByTitleRelevance("Hades", results).map { it.title })
        assertTrue(filterByTitleRelevance("Stardew Valley", results.take(2)).isEmpty())
        assertEquals(listOf("Hades II"), filterByTitleRelevance("hades: ii", results).map { it.title })
        assertEquals(
            listOf("The Witcher 3: Wild Hunt"),
            filterByTitleRelevance("Witcher III", listOf(GameStoreProduct("1", "The Witcher 3: Wild Hunt"))).map { it.title }
        )
    }

    @Test
    fun `search queries are normalized`() {
        assertEquals("The Witcher 3 Wild Hunt Remastered", normalizeSearchQuery("The Witcher 3: Wild Hunt — Remastered"))
        assertEquals("Assassin's Creed", normalizeSearchQuery("  Assassin's Creed! "))
    }

    @Test
    fun `Epic cover images are requested as small thumbnails`() {
        assertEquals(
            "https://cdn1.epicgames.com/offer/x/cover-1?h=96&resize=1&w=72",
            epicThumbnailUrl("https://cdn1.epicgames.com/offer/x/cover-1")
        )
        assertEquals("https://example.test/tall.jpg", epicThumbnailUrl("https://example.test/tall.jpg"))
    }
}
