package org.gameyfin.app.core.security

import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import kotlin.test.assertEquals

class CustomAuthenticationEntryPointTest {

    private lateinit var entryPoint: CustomAuthenticationEntryPoint
    private lateinit var request: HttpServletRequest
    private lateinit var response: HttpServletResponse
    private lateinit var authException: AuthenticationException

    @BeforeEach
    fun setup() {
        entryPoint = CustomAuthenticationEntryPoint()
        request = mockk<HttpServletRequest>()
        response = mockk<HttpServletResponse>()
        authException = BadCredentialsException("Test exception")
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `commence should redirect to login when direct parameter is 1`() {
        every { request.getParameter("direct") } returns "1"
        every { response.sendRedirect(any()) } just Runs

        entryPoint.commence(request, response, authException)

        verify(exactly = 1) { response.sendRedirect("/login") }
    }

    @Test
    fun `commence should redirect to SSO when direct parameter is not 1`() {
        every { request.getParameter("direct") } returns "0"
        every { response.sendRedirect(any()) } just Runs

        entryPoint.commence(request, response, authException)

        verify(exactly = 1) { response.sendRedirect("/oauth2/authorization/${SecurityConfig.SSO_PROVIDER_KEY}") }
    }

    @Test
    fun `commence should redirect to SSO when direct parameter is null`() {
        every { request.getParameter("direct") } returns null
        every { response.sendRedirect(any()) } just Runs

        entryPoint.commence(request, response, authException)

        verify(exactly = 1) { response.sendRedirect("/oauth2/authorization/${SecurityConfig.SSO_PROVIDER_KEY}") }
    }

    @Test
    fun `commence should redirect to SSO when direct parameter is empty string`() {
        every { request.getParameter("direct") } returns ""
        every { response.sendRedirect(any()) } just Runs

        entryPoint.commence(request, response, authException)

        verify(exactly = 1) { response.sendRedirect("/oauth2/authorization/${SecurityConfig.SSO_PROVIDER_KEY}") }
    }

    @Test
    fun `commence should redirect to SSO when direct parameter is 2`() {
        every { request.getParameter("direct") } returns "2"
        every { response.sendRedirect(any()) } just Runs

        entryPoint.commence(request, response, authException)

        verify(exactly = 1) { response.sendRedirect("/oauth2/authorization/${SecurityConfig.SSO_PROVIDER_KEY}") }
    }

    @Test
    fun `commence should redirect to SSO when direct parameter is any non-1 value`() {
        every { request.getParameter("direct") } returns "true"
        every { response.sendRedirect(any()) } just Runs

        entryPoint.commence(request, response, authException)

        verify(exactly = 1) { response.sendRedirect("/oauth2/authorization/${SecurityConfig.SSO_PROVIDER_KEY}") }
    }

    @Test
    fun `commence should work with null authException`() {
        every { request.getParameter("direct") } returns "1"
        every { response.sendRedirect(any()) } just Runs

        entryPoint.commence(request, response, null)

        verify(exactly = 1) { response.sendRedirect("/login") }
    }

    @Test
    fun `commence should use correct SSO provider key`() {
        every { request.getParameter("direct") } returns null
        every { response.sendRedirect(any()) } just Runs

        entryPoint.commence(request, response, authException)

        val expectedUrl = "/oauth2/authorization/${SecurityConfig.SSO_PROVIDER_KEY}"
        verify(exactly = 1) { response.sendRedirect(expectedUrl) }
        assertEquals("oidc", SecurityConfig.SSO_PROVIDER_KEY)
    }
}