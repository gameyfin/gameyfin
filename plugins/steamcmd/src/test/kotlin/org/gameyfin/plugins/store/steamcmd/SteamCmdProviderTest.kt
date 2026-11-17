package org.gameyfin.plugins.store.steamcmd

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SteamCmdProviderTest {
    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private val failingHttp = SteamCmdHttp { error("no network in tests") }

    @Test
    fun `parses Steam public and named branches without treating build IDs as versions`() {
        val response = json(
            """
            {"data":{"413150":{"depots":{"branches":{
              "public":{"buildid":"16826371","timebuildupdated":"1734825749"},
              "legacy_1.6.8":{"buildid":"14198583","timeupdated":"1730756892"}
            }}}}}
            """.trimIndent()
        )

        val versions = parseSteamVersions(response, "413150")

        assertEquals(2, versions.size)
        val public = versions.single { it.branch.isEmpty() }
        assertEquals("16826371", public.buildId)
        assertEquals("Public build 16826371 (2024-12-22)", public.label)
        assertEquals(Instant.ofEpochSecond(1734825749), public.releasedAt)
        assertEquals("legacy_1.6.8", versions.single { it.branch.isNotEmpty() }.branch)
        assertEquals("16826371", versions.first().buildId)
    }

    @Test
    fun `version response without branches fails`() {
        assertFailsWith<IllegalStateException> { parseSteamVersions(json("{}"), "1") }
    }

    @Test
    fun `provider rejects nonnumeric product IDs before making requests`() {
        assertFailsWith<IllegalArgumentException> { SteamCmdProvider(failingHttp).fetchVersions("1?other") }
    }

    @Test
    fun `fetches versions from steamcmd and provides nothing else`() {
        val urls = mutableListOf<String>()
        val http = SteamCmdHttp { url ->
            urls += url
            json("""{"data":{"10":{"depots":{"branches":{"public":{"buildid":"5"}}}}}}""")
        }
        val provider = SteamCmdProvider(http)

        assertEquals(listOf("5"), provider.fetchVersions("10").map { it.buildId })
        assertEquals(listOf("https://api.steamcmd.net/v1/info/10"), urls)
        assertTrue(provider.fetchDlc("10").isEmpty())
        assertTrue(provider.search("anything").isEmpty())
    }

    @Test
    fun `store info matches the Steam store`() {
        val store = SteamCmdProvider(failingHttp).store
        assertEquals("steam", store.id)
        assertEquals("413150", store.parseProductId("https://store.steampowered.com/app/413150/Stardew_Valley/"))
        assertNull(store.parseProductId("abc"))
    }
}
