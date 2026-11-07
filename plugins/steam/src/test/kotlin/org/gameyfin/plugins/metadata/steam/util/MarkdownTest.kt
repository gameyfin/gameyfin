package org.gameyfin.plugins.metadata.steam.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownTest {
    @Test
    fun `preserves missing and blank descriptions`() {
        assertEquals(null, toMarkdown(null))
        assertEquals("", toMarkdown(""))
        assertEquals("  ", toMarkdown("  "))
    }

    @Test
    fun `converts Steam HTML to readable Markdown`() {
        val result = toMarkdown("""
            <h2>About the game</h2>
            <p>Explore <strong>worlds</strong> &amp; <em>adventures</em>.<br>Play together.</p>
            <p><a href="https://example.com">Learn more</a></p>
            <ul><li>Explore<ul><li>Discover</li></ul></li></ul>
            <ol><li>Start</li><li>Finish</li></ol>
        """.trimIndent())!!
        assertTrue(result.contains("## About the game"), result)
        assertTrue(result.contains("**worlds**"), result)
        assertTrue(result.contains("*adventures*"), result)
        assertTrue(result.contains(" & "), result)
        assertTrue(result.contains("\nPlay together."), result)
        assertTrue(result.contains("[Learn more](https://example.com)"), result)
        assertTrue(Regex("(?m)^[-*+] Explore").containsMatchIn(result), result)
        assertTrue(Regex("(?m)^ +[-*+] Discover").containsMatchIn(result), result)
        assertTrue(result.contains("1. Start"), result)
        assertTrue(result.contains("2. Finish"), result)
    }

    @Test
    fun `keeps plain text readable and decodes entities`() {
        assertEquals("A simple game", toMarkdown("A simple game")?.trim())
        assertEquals("Tom & Jerry", toMarkdown("<p>Tom &amp; Jerry</p>")?.trim())
    }
}
