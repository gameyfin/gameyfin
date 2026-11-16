package org.gameyfin.app.core.download.files

import org.gameyfin.pluginapi.download.DownloadMetadata
import org.gameyfin.pluginapi.download.LinkDownload
import org.gameyfin.pluginapi.download.RangeDownload
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadResponsesTest {
    @Test
    fun `browser links are escaped inert HTML instead of redirects`() {
        val url = "custom-client:game?name=\"</a><script>alert('x')</script>&other=ü"
        // URL syntax itself must be valid; encoded characters remain encoded in the result.
        val link = LinkDownload(url.replace("\"", "%22").replace("<", "%3C").replace(">", "%3E"))
        val request = MockHttpServletRequest("GET", "/download/1").apply { setParameter("client", "browser") }
        val response = DownloadResponses.link(link, request)
        val output = ByteArrayOutputStream()
        response.body!!.writeTo(output)
        val html = output.toString(Charsets.UTF_8)
        assertEquals(200, response.statusCode.value())
        assertEquals("text/html", response.headers.contentType.toString())
        assertEquals("SAMEORIGIN", response.headers.getFirst("X-Frame-Options"))
        assertEquals("no-store", response.headers.cacheControl)
        assertTrue(response.headers.getFirst("Content-Security-Policy")!!.contains("default-src 'none'"))
        assertTrue(html.contains("id=\"download-link\""))
        assertTrue(html.contains("&amp;other=ü"))
        assertFalse(html.contains("<script"))
        assertFalse(response.headers.containsHeader("Content-Disposition"))
        assertNull(response.headers.location)
    }

    @Test
    fun `link responses reject executable document schemes`() {
        for (scheme in listOf("javascript", "JAVASCRIPT", "data", "vbscript")) {
            assertFailsWith<IllegalArgumentException> {
                DownloadResponses.link(LinkDownload("$scheme:payload"), MockHttpServletRequest())
            }
        }
    }

    private class Resource(val bytes: ByteArray = "0123456789".toByteArray()) : RangeDownload {
        override val metadata = DownloadMetadata("bin", bytes.size.toLong())
        override val etag = "\"version1\""
        var closed = false
        var opened = false
        override fun openStream(offset: Long, length: Long): InputStream {
            opened = true
            return bytes.copyOfRange(offset.toInt(), (offset + length).toInt()).inputStream()
        }

        override fun close() {
            closed = true
        }
    }

    @Test
    fun `single range framing and contents`() {
        for ((header, expected) in mapOf(
            "bytes=2-4" to "234",
            "bytes=7-" to "789",
            "bytes=-3" to "789",
            "bytes=8-99" to "89",
            "bytes=-99" to "0123456789"
        )) {
            val resource = Resource()
            val request = MockHttpServletRequest("GET", "/download/1").apply { addHeader("Range", header) }
            var started = 0
            val response = DownloadResponses.range(resource, request, "attachment", { started++ }) { input, output ->
                input.copyTo(output)
            }
            assertEquals(206, response.statusCode.value())
            assertEquals(expected.length.toLong(), response.headers.contentLength)
            assertEquals("bytes", response.headers.getFirst("Accept-Ranges"))
            val start = "0123456789".indexOf(expected)
            assertEquals("bytes $start-${start + expected.length - 1}/10", response.headers.getFirst("Content-Range"))
            val output = ByteArrayOutputStream()
            response.body!!.writeTo(output)
            assertEquals(expected, output.toString())
            assertEquals(1, started)
            assertTrue(resource.closed)
        }
    }

    @Test
    fun `unsatisfiable ranges close without starting downloads`() {
        for ((header, bytes) in listOf("bytes=10-" to "0123456789", "bytes=-0" to "0123456789", "bytes=0-" to "")) {
            val resource = Resource(bytes.toByteArray())
            val request = MockHttpServletRequest("GET", "/").apply { addHeader("Range", header) }
            val response =
                DownloadResponses.range(resource, request, "attachment", { error("started") }) { _, _ -> error("body") }
            assertEquals(416, response.statusCode.value())
            assertEquals("bytes */${bytes.length}", response.headers.getFirst("Content-Range"))
            assertFalse(resource.opened)
            assertTrue(resource.closed)
        }
    }

    @Test
    fun `invalid multiple and unknown ranges fall back to full response`() {
        for (header in listOf(
            "bytes=bad",
            "bytes=9-2",
            "bytes=0-1,4-5",
            "items=0-2",
            "bytes=-",
            "bytes=999999999999999999999-"
        )) {
            val request = MockHttpServletRequest("GET", "/").apply { addHeader("Range", header) }
            val response =
                DownloadResponses.range(Resource(), request, "attachment", {}) { input, output -> input.copyTo(output) }
            assertEquals(200, response.statusCode.value())
            val output = ByteArrayOutputStream()
            response.body!!.writeTo(output)
            assertEquals("0123456789", output.toString())
        }
    }

    @Test
    fun `If-Range must match strong tag and HEAD never opens body`() {
        for (validator in listOf("\"version1\"", "\"old\"", "W/\"version1\"", "Wed, 21 Oct 2015 07:28:00 GMT")) {
            val request = MockHttpServletRequest("GET", "/").apply {
                addHeader("Range", "bytes=5-"); addHeader("If-Range", validator)
            }
            val response =
                DownloadResponses.range(Resource(), request, "attachment", {}) { input, output -> input.copyTo(output) }
            assertEquals(if (validator == "\"version1\"") 206 else 200, response.statusCode.value())
            response.body!!.writeTo(ByteArrayOutputStream())
        }
        val resource = Resource()
        val request = MockHttpServletRequest("HEAD", "/").apply { addHeader("Range", "bytes=5-") }
        val response =
            DownloadResponses.range(resource, request, "attachment", { error("started") }) { _, _ -> error("body") }
        assertEquals(200, response.statusCode.value())
        assertEquals(10, response.headers.contentLength)
        assertNull(response.body)
        assertFalse(resource.opened)
        assertTrue(resource.closed)
    }
}
