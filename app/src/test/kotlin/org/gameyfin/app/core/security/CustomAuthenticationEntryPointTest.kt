package org.gameyfin.app.core.security

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.BadCredentialsException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals

class CustomAuthenticationEntryPointTest {
    @Test
    fun `browser failures go to gateway with original path and encoded query`() {
        val request = MockHttpServletRequest("GET", "/game/42").apply {
            queryString = "search=a%2Bb&sort=name"
        }
        val response = MockHttpServletResponse()
        CustomAuthenticationEntryPoint().commence(request, response, BadCredentialsException("Login required"))
        val target = URI(response.redirectedUrl!!)
        assertEquals("/loginredirect", target.path)
        assertEquals("continue=/game/42?search=a%2Bb&sort=name",
            URLDecoder.decode(target.rawQuery, StandardCharsets.UTF_8))
    }

    @Test
    fun `direct login override reaches gateway without selecting a provider`() {
        val request = MockHttpServletRequest("GET", "/library/1").apply { addParameter("direct", "1") }
        val response = MockHttpServletResponse()
        CustomAuthenticationEntryPoint().commence(request, response, BadCredentialsException("Login required"))
        assertEquals("/loginredirect?continue=%2Flibrary%2F1&direct=1", response.redirectedUrl)
    }
}
