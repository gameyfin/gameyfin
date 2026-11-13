package org.gameyfin.app.core.security

import io.mockk.every
import io.mockk.mockk
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.setup.SetupService
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import kotlin.test.assertEquals

class LoginRedirectControllerTest {
    private val config = mockk<ConfigService>()
    private val setup = mockk<SetupService>()
    private val controller = LoginRedirectController(config, setup)

    @ParameterizedTest
    @CsvSource("false,false,,/setup", "false,true,,/setup", "true,false,,/login",
        "true,true,,/oauth2/authorization/oidc", "true,true,1,/login", "true,true,0,/oauth2/authorization/oidc")
    fun `gateway selects setup or configured login`(complete: Boolean, sso: Boolean, direct: String?, target: String) {
        every { setup.isSetupCompleted() } returns complete
        every { config.get(ConfigProperties.SSO.OIDC.Enabled) } returns sso
        val request = MockHttpServletRequest().apply { direct?.let { addParameter("direct", it) } }
        val response = MockHttpServletResponse()
        controller.loginRedirect(request, response)
        assertEquals(target, response.redirectedUrl)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `both login modes preserve return path across requests and consume it once`(sso: Boolean) {
        every { setup.isSetupCompleted() } returns true
        every { config.get(ConfigProperties.SSO.OIDC.Enabled) } returns sso
        val session = MockHttpSession()
        val start = MockHttpServletRequest().apply {
            setSession(session)
            addParameter("continue", "/game/42?search=a%2Bb&sort=name#details")
        }
        controller.loginRedirect(start, MockHttpServletResponse())
        val callback = MockHttpServletRequest().apply { setSession(session) }
        val response = MockHttpServletResponse()
        controller.loginSuccess(callback, response)
        assertEquals("/game/42?search=a%2Bb&sort=name#details", response.redirectedUrl)
        assertEquals("/", LoginReturnUrl.consume(callback))
    }

    @ParameterizedTest
    @ValueSource(strings = ["https://evil.example", "//evil.example", "/\\evil.example", "/%2f/evil.example",
        "/%5cevil.example", "/login", "/login/", "/loginredirect", "/login/success", "/setup", "/logout",
        "/oauth2/authorization/oidc", "/foo/../login", "/foo/..%2flogin", "/login;jsessionid=123",
        "/bad\r\nLocation: evil", "relative", ""])
    fun `unsafe and recursive return destinations fall back to root`(target: String) {
        val request = MockHttpServletRequest()
        LoginReturnUrl.save(request, target)
        assertEquals("/", LoginReturnUrl.consume(request))
    }

    @Test
    fun `starting another login clears stale destination`() {
        val request = MockHttpServletRequest()
        LoginReturnUrl.save(request, "/game/42")
        LoginReturnUrl.save(request, null)
        assertEquals("/", LoginReturnUrl.consume(request))
    }
}
