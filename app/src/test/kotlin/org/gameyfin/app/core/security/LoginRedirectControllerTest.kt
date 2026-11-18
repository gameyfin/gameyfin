package org.gameyfin.app.core.security

import io.mockk.every
import io.mockk.mockk
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.setup.SetupService
import org.gameyfin.app.setup.PluginOnboardingService
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
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
    private val onboarding = mockk<PluginOnboardingService>(relaxed = true)
    private val controller = LoginRedirectController(config, setup, onboarding)

    @Test
    fun `initial account setup retains a validated login destination`() {
        every { setup.isSetupCompleted() } returns false
        val request = MockHttpServletRequest().apply { addParameter("continue", "/game/42") }
        val response = MockHttpServletResponse()
        controller.loginRedirect(request, response)
        assertEquals("/setup", response.redirectedUrl)
        assertEquals("/game/42", LoginReturnUrl.consume(request))
    }

    @Test
    fun `pending admin visits onboarding without consuming validated return path`() {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("admin", "", "ROLE_ADMIN")
        try {
            every { onboarding.isPending() } returns true
            val request = MockHttpServletRequest()
            LoginReturnUrl.save(request, "/game/42?q=test#details")
            val response = MockHttpServletResponse()
            controller.loginSuccess(request, response)
            assertEquals("/setup/plugins", response.redirectedUrl)
            every { onboarding.isPending() } returns false
            val finished = MockHttpServletResponse()
            controller.loginSuccess(request, finished)
            assertEquals("/game/42?q=test#details", finished.redirectedUrl)
        } finally { SecurityContextHolder.clearContext() }
    }

    @Test
    fun `ordinary user bypasses pending onboarding`() {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("user", "", "ROLE_USER")
        try {
            every { onboarding.isPending() } returns true
            val request = MockHttpServletRequest()
            val response = MockHttpServletResponse()
            controller.loginSuccess(request, response)
            assertEquals("/", response.redirectedUrl)
        } finally { SecurityContextHolder.clearContext() }
    }

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
    @CsvSource("false,,/setup", "true,,/oauth2/authorization/oidc", "true,1,/login")
    fun `gateway resumes the return path saved by the authentication entry point`(
        complete: Boolean, direct: String?, target: String
    ) {
        every { setup.isSetupCompleted() } returns complete
        every { config.get(ConfigProperties.SSO.OIDC.Enabled) } returns true
        val request = MockHttpServletRequest().apply {
            setSession(MockHttpSession())
            addParameter("resume", "1")
            direct?.let { addParameter("direct", it) }
        }
        LoginReturnUrl.save(request, "/download/42?q=a%2Bb")
        val response = MockHttpServletResponse()
        controller.loginRedirect(request, response)
        assertEquals(target, response.redirectedUrl)
        assertEquals("/download/42?q=a%2Bb", LoginReturnUrl.consume(request))
    }

    @ParameterizedTest
    @ValueSource(strings = ["https://evil.example", "//evil.example", "/\\evil.example", "/%2f/evil.example",
        "/%5cevil.example", "/login", "/login/", "/loginredirect", "/login/success", "/setup", "/setup/plugins", "/setup/%70lugins", "/logout",
        "/oauth2/authorization/oidc", "/foo/../login", "/foo/..%2flogin", "/login;jsessionid=123",
        "/bad\r\nLocation: evil", "relative", ""])
    fun `unsafe and recursive return destinations fall back to root`(target: String) {
        val request = MockHttpServletRequest()
        LoginReturnUrl.save(request, target)
        assertEquals("/", LoginReturnUrl.consume(request))
    }

    @Test
    fun `starting another login clears stale destination`() {
        every { setup.isSetupCompleted() } returns false
        val request = MockHttpServletRequest()
        LoginReturnUrl.save(request, "/game/42")
        controller.loginRedirect(request, MockHttpServletResponse())
        assertEquals("/", LoginReturnUrl.consume(request))
    }
}
