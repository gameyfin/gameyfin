package org.gameyfin.app.core.security

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.BadCredentialsException
import kotlin.test.assertEquals

class CustomAuthenticationEntryPointTest {
    @Test
    fun `browser failures store the original path and query in the session`() {
        val request = MockHttpServletRequest("GET", "/game/42").apply {
            queryString = "search=a%2Bb&sort=name"
        }
        val response = MockHttpServletResponse()
        CustomAuthenticationEntryPoint().commence(request, response, BadCredentialsException("Login required"))
        assertEquals("/loginredirect?resume=1", response.redirectedUrl)
        assertEquals("/game/42?search=a%2Bb&sort=name", LoginReturnUrl.consume(request))
    }

    @Test
    fun `direct login override reaches gateway without selecting a provider`() {
        val request = MockHttpServletRequest("GET", "/library/1").apply { addParameter("direct", "1") }
        val response = MockHttpServletResponse()
        CustomAuthenticationEntryPoint().commence(request, response, BadCredentialsException("Login required"))
        assertEquals("/loginredirect?resume=1&direct=1", response.redirectedUrl)
        assertEquals("/library/1", LoginReturnUrl.consume(request))
    }
}
