package org.gameyfin.plugins.store.itch

import org.gameyfin.pluginapi.gamestore.GameStoreProduct
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItchProviderTest {

    private fun searchResponse(html: String) = "<html><body>$html</body></html>"

    private fun cell(id: Int, href: String, title: String, author: String = "Figglewatts") =
        """<div dir="auto" data-game_id="$id" class="game_cell has_cover lazy_images"><div class="game_thumb">""" +
            """<a data-label="game:$id:thumb" href="$href" class="thumb_link game_link" tabindex="-1">""" +
            """<img width="315" data-lazy_src="https://img.itch.zone/abc/315x250%23c/x.png" class="lazy_loaded"/></a></div>""" +
            """<div class="game_cell_data"><div class="game_title"><a data-action="game_grid" href="$href" class="title game_link" data-label="game:$id:title">$title</a></div>""" +
            """<div class="game_author"><a data-action="game_grid" href="https://figglewatts.itch.io" data-label="user:1">$author</a></div></div></div>"""

    private val gamePage = """
        <div class="uploads"><p>Click download now to get access to the following files:</p><div id="upload_list_1" class="upload_list_widget base_widget">
        <div class="upload"><div class="info_column"><div class="upload_name"><strong title="xmoon-win32.zip" class="name">xmoon-win32.zip</strong> <span class="file_size"><span>2.9 MB</span></span> <span class="download_platforms"><span aria-hidden="true" title="Download for Windows" class="icon icon-windows8"></span> </span></div></div></div>
        <div class="upload"><div class="info_column"><div class="upload_name"><strong title="x-moon-love11.5.love" class="name">x-moon-love11.5.love</strong> <span class="file_size"><span>1 MB</span></span> <span class="download_platforms"></span></div><div class="build_row"><span class="version_name">Version love11.5-75072cf</span></div></div></div>
        <div class="upload"><div class="info_column"><div class="upload_name"><strong class="name">xmoon-linux.tar.gz</strong> <span class="download_platforms"><span class="icon icon-tux"></span> </span></div></div></div>
        <div class="upload"><div class="info_column"><div class="upload_name"><strong class="name">xmoon-win32.zip</strong> <span class="download_platforms"><span class="icon icon-windows8"></span> </span></div></div></div>
        </div></div><div id="game_comments_4836151" class="game_comments_widget base_widget"><span class="icon icon-tux"></span></div>
    """.trimIndent()

    @Test
    fun `parses search results`() {
        val http = ItchHttp {
            assertEquals("https://itch.io/search?type=games&q=LSD+Revamped", it)
            searchResponse(
                cell(631448, "https://figglewatts.itch.io/lsd-revamped", "LSD: Revamped") +
                    cell(2, "https://someone.itch.io/Tom-s_Game", "LSD Tom&#39;s &amp; Co")
            )
        }

        val results = ItchProvider(http).search("LSD: Revamped")

        assertEquals(2, results.size)
        assertEquals("figglewatts.itch.io/lsd-revamped", results[0].id)
        assertEquals("LSD: Revamped", results[0].title)
        assertEquals("Figglewatts", results[0].subtitle)
        assertEquals("https://img.itch.zone/abc/315x250%23c/x.png", results[0].imageUrl)
        assertEquals("someone.itch.io/tom-s_game", results[1].id)
        assertEquals("LSD Tom's & Co", results[1].title)
    }

    @Test
    fun `does not depend on the order of HTML attributes`() {
        val html = """<div class="game_cell" dir="auto" data-game_id="9"><a class="thumb_link game_link" href="https://a.itch.io/b" data-label="x"><img class="lazy_loaded" data-lazy_src="https://img.itch.zone/t.png"/></a>""" +
            """<div class="game_title"><a href="https://a.itch.io/b" data-action="game_grid" class="title game_link" data-label="y">Shuffled</a></div>""" +
            """<div class="game_author"><a href="https://a.itch.io" data-action="game_grid">Dev</a><svg><title>Verified</title></svg></div></div>"""

        val result = ItchProvider { searchResponse(html) }.search("shuffled").single()

        assertEquals("a.itch.io/b", result.id)
        assertEquals("Shuffled", result.title)
        assertEquals("Dev", result.subtitle)
        assertEquals("https://img.itch.zone/t.png", result.imageUrl)

        val page = """<div class="uploads"><div class="upload"><div><div class="upload_name"><strong title="g.zip" class="name">g.zip</strong></div><div class="build_row"><span class="x version_name" class="version_name">Version 2</span></div></div></div></div>"""
        assertEquals(listOf("2 (g.zip)"), parseItchUploads(page).map { it.label })
    }

    @Test
    fun `drops loosely matching results and ranks the best title first`() {
        fun p(title: String) = GameStoreProduct(id = "a.itch.io/x", title = title)
        val ranked = rankByTitle(
            listOf(p("Time loop"), p("Bumpy Ride at Bore IX"), p("A Bumpy Ride"), p("How to Bump Trains")),
            "A Bumpy Ride"
        )
        assertEquals(listOf("A Bumpy Ride", "Bumpy Ride at Bore IX"), ranked.map { it.title })
    }

    @Test
    fun `skips games on custom domains and short queries`() {
        val http = ItchHttp { searchResponse(cell(1, "https://example.com/game", "Custom Normal") + cell(2, "https://a.itch.io/b", "Normal Custom")) }
        assertEquals(listOf("a.itch.io/b"), ItchProvider(http).search("custom normal").map { it.id })
        assertTrue(ItchProvider { error("no request expected") }.search("a").isEmpty())
    }

    @Test
    fun `parses downloads as versions and drops other platforms`() {
        val http = ItchHttp {
            assertEquals("https://leafo.itch.io/x-moon", it)
            gamePage
        }

        val versions = ItchProvider(http).fetchVersions("leafo.itch.io/x-moon")

        assertEquals(listOf("xmoon-win32.zip", "x-moon-love11.5.love"), versions.map { it.buildId })
        assertEquals("xmoon-win32.zip", versions[0].label)
        assertEquals("love11.5-75072cf (x-moon-love11.5.love)", versions[1].label)
        assertTrue(versions.all { it.branch.isEmpty() && it.releasedAt == null })
    }

    @Test
    fun `page without downloads has no versions`() {
        assertTrue(parseItchUploads("<html>nothing</html>").isEmpty())
    }

    @Test
    fun `accepts IDs and itch io URLs only`() {
        val store = ItchProvider { error("no request expected") }.store
        assertEquals("leafo.itch.io/x-moon", store.parseProductId("leafo.itch.io/x-moon"))
        assertEquals("leafo.itch.io/x-moon", store.parseProductId("  https://Leafo.itch.io/X-Moon/devlog/1  "))
        assertEquals("leafo.itch.io/x-moon", store.parseProductId("http://leafo.itch.io/x-moon?ref=abc"))
        assertNull(store.parseProductId("https://example.com/x-moon"))
        assertNull(store.parseProductId("https://evil.example/a.itch.io/b"))
        assertNull(store.parseProductId("leafo.itch.io"))
        assertNull(store.parseProductId("a.itch.io/b/c"))
        assertNull(store.parseProductId("../etc/passwd"))
    }

    @Test
    fun `fetching versions validates the product ID`() {
        assertFailsWith<IllegalArgumentException> { ItchProvider { error("no request expected") }.fetchVersions("evil.example/x") }
    }

    @Test
    fun `only itch hosts are accepted for redirects`() {
        assertTrue(isItchHost("leafo.itch.io"))
        assertTrue(isItchHost("itch.io"))
        assertTrue(!isItchHost("itch.io.evil.example"))
        assertTrue(!isItchHost("evilitch.io"))
        assertTrue(!isItchHost(null))
    }
}
