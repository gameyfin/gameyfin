package org.gameyfin.app.games.versions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreThumbnailProxyTest {

    private val proxy = StoreThumbnailProxy()

    @Test
    fun `only https Epic CDN hosts are proxied`() {
        assertTrue(proxy.isProxyable("https://cdn1.epicgames.com/spt-assets/a.png?h=96&w=72"))
        assertFalse(proxy.isProxyable("http://cdn1.epicgames.com/a.png"))
        assertFalse(proxy.isProxyable("https://epicgames.com.evil.example/a.png"))
        assertFalse(proxy.isProxyable("https://evilepicgames.com/a.png"))
        assertFalse(proxy.isProxyable("https://user@cdn1.epicgames.com/a.png"))
        assertFalse(proxy.isProxyable("https://cdn1.epicgames.com:8443/a.png"))
        assertFalse(proxy.isProxyable("http://localhost:8080/admin"))
        assertFalse(proxy.isProxyable("not a url"))
    }

    @Test
    fun `fetch refuses hosts that are not allowed`() {
        assertNull(proxy.fetch("http://127.0.0.1:8080/"))
    }

    @Test
    fun `rewrite only changes proxied URLs`() {
        val epic = "https://cdn1.epicgames.com/a.png?h=96&w=72"
        assertEquals(
            "/images/store-thumbnail?url=https%3A%2F%2Fcdn1.epicgames.com%2Fa.png%3Fh%3D96%26w%3D72",
            proxy.rewrite(epic)
        )
        val steam = "https://cdn.cloudflare.steamstatic.com/a.jpg"
        assertEquals(steam, proxy.rewrite(steam))
        assertNull(proxy.rewrite(null))
    }
}
