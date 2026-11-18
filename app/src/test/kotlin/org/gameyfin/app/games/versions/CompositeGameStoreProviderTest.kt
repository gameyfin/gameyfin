package org.gameyfin.app.games.versions

import org.gameyfin.pluginapi.gamestore.GameStoreDlc
import org.gameyfin.pluginapi.gamestore.GameStoreDlcKind
import org.gameyfin.pluginapi.gamestore.GameStoreProduct
import org.gameyfin.pluginapi.gamestore.GameStoreProvider
import org.gameyfin.pluginapi.gamestore.GameStoreVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CompositeGameStoreProviderTest {

    private class Source(
        val versions: () -> List<GameStoreVersion> = { emptyList() },
        val products: () -> List<GameStoreProduct> = { emptyList() },
        val dlc: () -> List<GameStoreDlc> = { emptyList() },
        val editions: () -> List<String> = { emptyList() }
    ) : GameStoreProvider {
        override val store = GOG_INFO
        override fun search(query: String) = products()
        override fun fetchVersions(productId: String) = versions()
        override fun fetchDlc(productId: String) = dlc()
        override fun suggestEditions(productId: String) = editions()
    }

    private fun composite(vararg sources: Source) = CompositeGameStoreProvider(GOG_INFO, sources.toList())
    private fun version(build: String) = GameStoreVersion(build, label = build)

    @Test
    fun `versions come from the first source that has some, without duplicates`() {
        val result = composite(
            Source(versions = { listOf(version("a")) }),
            Source(versions = { listOf(version("a"), version("b")) })
        ).fetchVersions("1")
        assertEquals(listOf("a"), result.map { it.buildId })
    }

    @Test
    fun `versions fall back when the primary source is empty or fails`() {
        val fallback = Source(versions = { listOf(version("x")) })
        assertEquals("x", composite(Source(), fallback).fetchVersions("1").single().buildId)
        assertEquals("x", composite(Source(versions = { error("down") }), fallback).fetchVersions("1").single().buildId)
    }

    @Test
    fun `a failure is rethrown only if no source has a result`() {
        assertFailsWith<IllegalStateException> {
            composite(Source(versions = { error("down") }), Source()).fetchVersions("1")
        }
        assertEquals(emptyList(), composite(Source(), Source()).fetchVersions("1"))
    }

    @Test
    fun `search falls back to the next source`() {
        val product = GameStoreProduct("1", "Game")
        assertEquals(listOf(product), composite(Source(), Source(products = { listOf(product) })).search("g"))
    }

    @Test
    fun `DLC is merged and deduplicated with the primary source winning`() {
        val result = composite(
            Source(dlc = { listOf(GameStoreDlc("1", "Primary", GameStoreDlcKind.DLC)) }),
            Source(
                dlc = {
                    listOf(
                        GameStoreDlc("1", "Fallback", GameStoreDlcKind.BONUS),
                        GameStoreDlc("2", "Other", GameStoreDlcKind.DLC)
                    )
                }
            )
        ).fetchDlc("1")
        assertEquals(listOf("Primary", "Other"), result.map { it.title })
    }

    @Test
    fun `DLC survives one failing source`() {
        val result = composite(
            Source(dlc = { error("down") }),
            Source(dlc = { listOf(GameStoreDlc("2", "Other", GameStoreDlcKind.DLC)) })
        ).fetchDlc("1")
        assertEquals(1, result.size)
    }

    @Test
    fun `edition names are merged ignoring case`() {
        val result = composite(
            Source(editions = { listOf("Deluxe Edition") }),
            Source(editions = { listOf("deluxe edition", "Ultimate") })
        ).suggestEditions("1")
        assertEquals(listOf("Deluxe Edition", "Ultimate"), result)
    }

    @Test
    fun `sources of one store are combined by priority into a single store`() {
        val low = Source(versions = { listOf(version("low")) })
        val high = Source(versions = { listOf(version("high")) })
        val solo = Source()
        val stores = combineStoreSources(
            listOf(
                activeStore(GOG_INFO, low, priority = 1),
                activeStore(GOG_INFO, high, priority = 5),
                activeStore(STEAM_INFO, solo)
            )
        )
        assertEquals(listOf("gog", "steam"), stores.map { it.id })
        assertEquals("high", stores[0].provider.fetchVersions("1").single().buildId)
        assertEquals(5, stores[0].priority)
        assertSame(solo, stores[1].provider)
    }

    @Test
    fun `stores are ordered by plugin priority, then by name`() {
        val stores = combineStoreSources(
            listOf(
                activeStore(EPIC_INFO, priority = 1),
                activeStore(GOG_INFO, priority = 3),
                activeStore(STEAM_INFO, priority = 9),
                activeStore(STEAM_INFO, priority = 2)
            )
        )
        assertEquals(listOf("steam", "gog", "epic"), stores.map { it.id })

        val tied = combineStoreSources(listOf(activeStore(STEAM_INFO), activeStore(EPIC_INFO), activeStore(GOG_INFO)))
        assertEquals(listOf("epic", "gog", "steam"), tied.map { it.id })
    }
}
