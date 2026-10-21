package org.gameyfin.app.core.plugins.marketplace

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MarketplaceUrlResolverTest {

    private fun resolve(input: String, allowInsecure: Boolean = false): String =
        MarketplaceUrlResolver.resolveIndexUrl(input, allowInsecure).toString()

    @Test
    fun `index file name is plugins json`() {
        assertEquals("plugins.json", MarketplaceUrlResolver.INDEX_FILE_NAME)
    }

    // --- GitHub URLs ---

    @Test
    fun `GitHub repository root resolves to plugins json on default branch`() {
        assertEquals(
            "https://raw.githubusercontent.com/gameyfin/marketplace/HEAD/plugins.json",
            resolve("https://github.com/gameyfin/marketplace")
        )
    }

    @Test
    fun `GitHub repository root with trailing slash resolves to plugins json on default branch`() {
        assertEquals(
            "https://raw.githubusercontent.com/gameyfin/marketplace/HEAD/plugins.json",
            resolve("https://github.com/gameyfin/marketplace/")
        )
    }

    @Test
    fun `GitHub repository URL with git suffix is supported`() {
        assertEquals(
            "https://raw.githubusercontent.com/gameyfin/marketplace/HEAD/plugins.json",
            resolve("https://github.com/gameyfin/marketplace.git")
        )
    }

    @Test
    fun `GitHub www host is supported`() {
        assertEquals(
            "https://raw.githubusercontent.com/gameyfin/marketplace/HEAD/plugins.json",
            resolve("https://www.github.com/gameyfin/marketplace")
        )
    }

    @Test
    fun `GitHub host is matched case insensitively`() {
        assertEquals(
            "https://raw.githubusercontent.com/gameyfin/marketplace/HEAD/plugins.json",
            resolve("https://GitHub.com/gameyfin/marketplace")
        )
    }

    @Test
    fun `GitHub URL without scheme is treated as https`() {
        assertEquals(
            "https://raw.githubusercontent.com/gameyfin/marketplace/HEAD/plugins.json",
            resolve("github.com/gameyfin/marketplace")
        )
    }

    @Test
    fun `GitHub tree URL with branch only resolves to plugins json in branch root`() {
        assertEquals(
            "https://raw.githubusercontent.com/gameyfin/marketplace/main/plugins.json",
            resolve("https://github.com/gameyfin/marketplace/tree/main")
        )
    }

    @Test
    fun `GitHub tree URL with directory resolves to plugins json in that directory`() {
        assertEquals(
            "https://raw.githubusercontent.com/gameyfin/marketplace/dev/some/dir/plugins.json",
            resolve("https://github.com/gameyfin/marketplace/tree/dev/some/dir")
        )
    }

    @Test
    fun `GitHub tree URL with trailing slash resolves to plugins json in that directory`() {
        assertEquals(
            "https://raw.githubusercontent.com/gameyfin/marketplace/dev/dir/plugins.json",
            resolve("https://github.com/gameyfin/marketplace/tree/dev/dir/")
        )
    }

    @Test
    fun `GitHub blob URL resolves to the raw file`() {
        assertEquals(
            "https://raw.githubusercontent.com/gameyfin/marketplace/main/index/custom.json",
            resolve("https://github.com/gameyfin/marketplace/blob/main/index/custom.json")
        )
    }

    @Test
    fun `GitHub blob URL in repository root resolves to the raw file`() {
        assertEquals(
            "https://raw.githubusercontent.com/gameyfin/marketplace/v1.0/plugins.json",
            resolve("https://github.com/gameyfin/marketplace/blob/v1.0/plugins.json")
        )
    }

    @Test
    fun `GitHub URL query and fragment are dropped`() {
        assertEquals(
            "https://raw.githubusercontent.com/gameyfin/marketplace/HEAD/plugins.json",
            resolve("https://github.com/gameyfin/marketplace?tab=readme#top")
        )
    }

    @Test
    fun `insecure GitHub URL is resolved to https raw URL when insecure URLs are allowed`() {
        assertEquals(
            "https://raw.githubusercontent.com/gameyfin/marketplace/HEAD/plugins.json",
            resolve("http://github.com/gameyfin/marketplace", allowInsecure = true)
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://github.com",
            "https://github.com/",
            "https://github.com/gameyfin",
            "https://github.com/gameyfin/marketplace/issues",
            "https://github.com/gameyfin/marketplace/tree",
            "https://github.com/gameyfin/marketplace/blob/main",
            "https://github.com/gameyfin/marketplace/releases/tag/v1",
        ]
    )
    fun `unsupported GitHub URL forms are rejected`(input: String) {
        assertFailsWith<IllegalArgumentException> { MarketplaceUrlResolver.resolveIndexUrl(input) }
    }

    @Test
    fun `GitHub URL with credentials is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            MarketplaceUrlResolver.resolveIndexUrl("https://user:token@github.com/gameyfin/marketplace")
        }
    }

    // --- Other URLs ---

    @Test
    fun `URL ending with json is used as is`() {
        assertEquals("https://example.com/market/index.json", resolve("https://example.com/market/index.json"))
    }

    @Test
    fun `URL ending with json is matched case insensitively`() {
        assertEquals("https://example.com/market/INDEX.JSON", resolve("https://example.com/market/INDEX.JSON"))
    }

    @Test
    fun `json URL keeps the query but drops the fragment`() {
        assertEquals(
            "https://example.com/market/index.json?v=2",
            resolve("https://example.com/market/index.json?v=2#section")
        )
    }

    @Test
    fun `raw githubusercontent json URL is used as is`() {
        val url = "https://raw.githubusercontent.com/gameyfin/marketplace/main/plugins.json"
        assertEquals(url, resolve(url))
    }

    @Test
    fun `directory URL resolves to plugins json in that directory`() {
        assertEquals("https://example.com/market/plugins.json", resolve("https://example.com/market"))
    }

    @Test
    fun `directory URL with trailing slash resolves to plugins json in that directory`() {
        assertEquals("https://example.com/market/plugins.json", resolve("https://example.com/market/"))
    }

    @Test
    fun `host only URL resolves to plugins json in root`() {
        assertEquals("https://example.com/plugins.json", resolve("https://example.com"))
    }

    @Test
    fun `directory URL keeps port and query and drops fragment`() {
        assertEquals(
            "https://example.com:8443/market/plugins.json?token=abc",
            resolve("https://example.com:8443/market?token=abc#frag")
        )
    }

    @Test
    fun `URL path is normalized`() {
        assertEquals("https://example.com/b/plugins.json", resolve("https://example.com/a/../b"))
    }

    @Test
    fun `input is trimmed`() {
        assertEquals("https://example.com/market/plugins.json", resolve("   https://example.com/market \n"))
    }

    @Test
    fun `input without scheme is treated as https`() {
        assertEquals("https://example.com/market/plugins.json", resolve("example.com/market"))
    }

    @Test
    fun `upper case https scheme is accepted`() {
        val uri = MarketplaceUrlResolver.resolveIndexUrl("HTTPS://example.com/market")
        assertEquals("https", uri.scheme.lowercase())
        assertEquals("/market/plugins.json", uri.path)
    }

    // --- Scheme checks ---

    @Test
    fun `http URL is rejected by default`() {
        val e = assertFailsWith<IllegalArgumentException> {
            MarketplaceUrlResolver.resolveIndexUrl("http://example.com/market")
        }
        assertTrue(e.message!!.contains("Unsupported URL scheme"))
    }

    @Test
    fun `http URL is accepted when insecure URLs are allowed`() {
        assertEquals(
            "http://127.0.0.1:8080/market/plugins.json",
            resolve("http://127.0.0.1:8080/market", allowInsecure = true)
        )
    }

    @Test
    fun `file URL is rejected by default`() {
        assertFailsWith<IllegalArgumentException> {
            MarketplaceUrlResolver.resolveIndexUrl("file:///opt/market")
        }
    }

    @Test
    fun `file directory URL is accepted when insecure URLs are allowed`() {
        val uri = MarketplaceUrlResolver.resolveIndexUrl("file:///opt/market", allowInsecure = true)
        assertEquals("file", uri.scheme)
        assertEquals("/opt/market/plugins.json", uri.path)
    }

    @Test
    fun `file json URL is accepted when insecure URLs are allowed`() {
        val uri = MarketplaceUrlResolver.resolveIndexUrl("file:///opt/market/index.json", allowInsecure = true)
        assertEquals("file", uri.scheme)
        assertEquals("/opt/market/index.json", uri.path)
    }

    @ParameterizedTest
    @ValueSource(strings = ["ftp://example.com/market", "javascript:alert(1)", "mailto:someone@example.com"])
    fun `other schemes are rejected even when insecure URLs are allowed`(input: String) {
        assertFailsWith<IllegalArgumentException> { MarketplaceUrlResolver.resolveIndexUrl(input, allowInsecure = true) }
    }

    // --- Invalid input ---

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "\t\n"])
    fun `empty input is rejected`(input: String) {
        val e = assertFailsWith<IllegalArgumentException> { MarketplaceUrlResolver.resolveIndexUrl(input) }
        assertEquals("URL must not be empty", e.message)
    }

    @Test
    fun `syntactically invalid URL is rejected`() {
        val e = assertFailsWith<IllegalArgumentException> {
            MarketplaceUrlResolver.resolveIndexUrl("https://exa mple.com/market")
        }
        assertTrue(e.message!!.startsWith("Invalid URL"))
    }

    @Test
    fun `URL without host is rejected`() {
        val e = assertFailsWith<IllegalArgumentException> { MarketplaceUrlResolver.resolveIndexUrl("https:///market") }
        assertEquals("URL must contain a host", e.message)
    }

    @Test
    fun `URL with credentials is rejected`() {
        val e = assertFailsWith<IllegalArgumentException> {
            MarketplaceUrlResolver.resolveIndexUrl("https://user:pass@example.com/market")
        }
        assertEquals("URLs with credentials are not supported", e.message)
    }

    @Test
    fun `http URL with credentials is rejected even when insecure URLs are allowed`() {
        assertFailsWith<IllegalArgumentException> {
            MarketplaceUrlResolver.resolveIndexUrl("http://user@example.com/market", allowInsecure = true)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["example.com:8443/market", "example.com:8443/market/"])
    fun `URL without scheme but with port gets https scheme`(input: String) {
        assertEquals("https://example.com:8443/market/plugins.json", resolve(input))
    }

    @Test
    fun `localhost with port without scheme gets https scheme`() {
        assertEquals("https://localhost:8080/plugins.json", resolve("localhost:8080"))
    }

    @Test
    fun `encoded characters in path and query are preserved`() {
        assertEquals(
            "https://example.com/my%20market/plugins.json?token=a%26b",
            resolve("https://example.com/my%20market?token=a%26b")
        )
    }

    @Test
    fun `fragment is removed from json URLs without changing the query`() {
        assertEquals("https://example.com/index.json?x=a%26b", resolve("https://example.com/index.json?x=a%26b#top"))
    }

    @Test
    fun `resolved URL is absolute`() {
        val uri: URI = MarketplaceUrlResolver.resolveIndexUrl("example.com")
        assertTrue(uri.isAbsolute)
    }
}
