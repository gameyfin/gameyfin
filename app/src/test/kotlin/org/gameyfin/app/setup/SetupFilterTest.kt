package org.gameyfin.app.setup

import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.security.LoginRedirectController
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SetupFilterTest {
    private val setupService = mockk<SetupService>()
    private val filter = SetupFilter(setupService)

    @ParameterizedTest
    @CsvSource("/login,false,/setup", "/setup,true,/loginredirect")
    fun `redirect stops the filter chain`(uri: String, setupComplete: Boolean, target: String) {
        every { setupService.isSetupCompleted() } returns setupComplete
        val request = MockHttpServletRequest("GET", uri)
        val response = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(request, response, chain)

        assertEquals(target, response.redirectedUrl)
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }

    @ParameterizedTest
    @CsvSource("/setup,false", "/login,true", "/loginredirect,true", "/loginredirect,false", "/login/oauth2/code/oidc,false", "/setup-other,true", "/login-other,false", "/,false", "/,true", "/VAADIN/test.js,false")
    fun `allowed requests continue without a redirect`(uri: String, setupComplete: Boolean) {
        every { setupService.isSetupCompleted() } returns setupComplete
        val request = MockHttpServletRequest("GET", uri)
        val response = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(request, response, chain)

        assertEquals(null, response.redirectedUrl)
        assertFalse(response.isCommitted)
        verify(exactly = 1) { chain.doFilter(request, response) }
    }

    @Test
    fun `login gateway on a new instance redirects to setup before reading SSO configuration`() {
        every { setupService.isSetupCompleted() } returns false
        val config = mockk<ConfigService>()
        val mvc = MockMvcBuilders.standaloneSetup(LoginRedirectController(config, setupService))
            .addFilters<StandaloneMockMvcBuilder>(filter)
            .build()

        mvc.perform(get("/loginredirect"))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("/setup"))

        verify { config wasNot Called }
    }
}
