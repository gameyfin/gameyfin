package org.gameyfin.plugins.store.steam

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.gameyfin.pluginapi.gamestore.GameStoreDlc
import org.gameyfin.pluginapi.gamestore.GameStoreDlcKind
import org.gameyfin.pluginapi.gamestore.GameStoreProduct
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SteamStoreProviderTest {
    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private val failingHttp = SteamStoreHttp { error("no network in tests") }

    @Test
    fun `provider rejects nonnumeric product IDs before making requests`() {
        val provider = SteamStoreProvider(failingHttp)
        assertFailsWith<IllegalArgumentException> { provider.fetchDlc("1/other") }
        assertTrue(provider.fetchVersions("1").isEmpty())
    }

    @Test
    fun `store info parses IDs and URLs`() {
        val store = SteamStoreProvider(failingHttp).store
        assertEquals("steam", store.id)
        assertEquals("413150", store.parseProductId(" 413150 "))
        assertEquals("413150", store.parseProductId("https://store.steampowered.com/app/413150/Stardew_Valley/"))
        assertEquals("413150", store.parseProductId("https://steamdb.info/app/413150/"))
        assertNull(store.parseProductId("abc"))
        assertNull(store.parseProductId("1/../2"))
    }

    @Test
    fun `parses Steam DLC app IDs`() {
        val root = json("""{"10":{"success":true,"data":{"dlc":[20,30,20,"bad"]}}}""")

        assertEquals(listOf("20", "30"), parseSteamDlcIds(root, "10"))
    }

    @Test
    fun `Steam failures give no DLC and no name`() {
        val root = json("""{"10":{"success":false}}""")

        assertTrue(parseSteamDlcIds(root, "10").isEmpty())
        assertNull(parseSteamAppName(root, "10"))
        assertNull(parseSteamAppType(root, "10"))
    }

    @Test
    fun `parses Steam app name and type`() {
        val root = json("""{"20":{"success":true,"data":{"name":" Skyrim Anniversary Upgrade ","type":"music"}}}""")

        assertEquals("Skyrim Anniversary Upgrade", parseSteamAppName(root, "20"))
        assertEquals("music", parseSteamAppType(root, "20"))
    }

    @Test
    fun `classifies extras`() {
        assertEquals(GameStoreDlcKind.BONUS, classifyExtra("Game Soundtrack"))
        assertEquals(GameStoreDlcKind.BONUS, classifyExtra("Whatever", isBonus = true))
        assertEquals(GameStoreDlcKind.EDITION_UPGRADE, classifyExtra("Anniversary Upgrade"))
        assertEquals(GameStoreDlcKind.DLC, classifyExtra("Extra Levels"))
    }

    @Test
    fun `fetches DLC with names, falling back when a lookup fails`() {
        val http = SteamStoreHttp { url ->
            when {
                "appids=10&" in url -> json("""{"10":{"success":true,"data":{"dlc":[20,30,40]}}}""")
                "appids=20&" in url -> json("""{"20":{"success":true,"data":{"name":"Anniversary Upgrade","type":"dlc"}}}""")
                "appids=30&" in url -> json("""{"30":{"success":true,"data":{"name":"Cool","type":"music"}}}""")
                else -> error("boom")
            }
        }

        assertEquals(
            listOf(
                GameStoreDlc("20", "Anniversary Upgrade", GameStoreDlcKind.EDITION_UPGRADE),
                GameStoreDlc("30", "Cool", GameStoreDlcKind.BONUS),
                GameStoreDlc("40", "Steam app 40", GameStoreDlcKind.DLC)
            ),
            SteamStoreProvider(http).fetchDlc("10")
        )
    }

    @Test
    fun `parses Steam search results and skips invalid entries`() {
        val results = parseSteamSearch(
            json(
                """{"total":3,"items":[
                    {"type":"app","name":"Stardew Valley","id":413150,"tiny_image":"https://example.test/a.jpg"},
                    {"type":"app","name":"","id":1},
                    {"type":"app","name":"No ID"}
                ]}"""
            )
        )

        assertEquals(listOf(GameStoreProduct("413150", "Stardew Valley", null, "https://example.test/a.jpg")), results)
        assertTrue(parseSteamSearch(json("{}")).isEmpty())
    }

    @Test
    fun `search normalizes the query and keeps only base games`() {
        val urls = mutableListOf<String>()
        val http = SteamStoreHttp { url ->
            synchronized(urls) { urls += url }
            when {
                "storesearch" in url -> json(
                    """{"items":[{"id":1,"name":"Game"},{"id":2,"name":"Game DLC"},{"id":3,"name":"Game OST"},{"id":4,"name":"Unknown"}]}"""
                )
                "appids=1&" in url -> json("""{"1":{"success":true,"data":{"type":"game"}}}""")
                "appids=2&" in url -> json("""{"2":{"success":true,"data":{"type":"dlc"}}}""")
                "appids=3&" in url -> json("""{"3":{"success":true,"data":{"type":"music"}}}""")
                else -> error("boom")
            }
        }

        val results = SteamStoreProvider(http).search("Wild Hunt — Remastered")

        assertEquals(listOf("1", "4"), results.map { it.id })
        assertTrue(urls.first().contains("term=Wild+Hunt+Remastered"))
    }

    @Test
    fun `normalizes search queries`() {
        assertEquals("Wild Hunt Remastered", normalizeSearchQuery("Wild Hunt — Remastered"))
        assertEquals("Assassin's Creed", normalizeSearchQuery("Assassin's Creed:"))
    }
}
