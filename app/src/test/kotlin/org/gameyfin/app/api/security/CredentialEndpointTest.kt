package org.gameyfin.app.api.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CredentialEndpointTest {
    private val credentials = mockk<ApiCredentialService>()
    private val endpoint = CredentialEndpoint(credentials)
    private val response = MockHttpServletResponse()

    private val view = CredentialView(
        "id", CredentialKind.SERVICE, "name", "gf_key_id", setOf("games:read"), Instant.EPOCH, null, null, null, "ACTIVE"
    )
    private val input = CredentialInput("name", scopes = setOf("games:read"))
    private val created = CreatedCredential(view, "secret")

    @AfterEach
    fun clear() = RequestContextHolder.resetRequestAttributes()

    @Test
    fun `read and management operations delegate to the service`() {
        every { credentials.list(true) } returns listOf(view)
        every { credentials.update("id", true, input) } returns view
        every { credentials.revoke("id", true) } returns Unit
        every { credentials.delete("id", true) } returns Unit

        assertEquals(listOf(view), endpoint.list(true))
        assertEquals(ApiScope.all, endpoint.scopes())
        assertSame(view, endpoint.update("id", true, input))
        endpoint.revoke("id", true)
        endpoint.delete("id", true)

        verify { credentials.revoke("id", true) }
        verify { credentials.delete("id", true) }
    }

    @Test
    fun `secret-bearing responses are marked as not cacheable`() {
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(MockHttpServletRequest(), response))
        every { credentials.create(true, input) } returns created
        every { credentials.regenerate("id", true) } returns created

        assertSame(created, endpoint.create(true, input))
        assertEquals("no-store", response.getHeader("Cache-Control"))

        val second = MockHttpServletResponse()
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(MockHttpServletRequest(), second))
        assertSame(created, endpoint.regenerate("id", true))
        assertEquals("no-store", second.getHeader("Cache-Control"))
    }

    @Test
    fun `secrets are returned even without a servlet request`() {
        RequestContextHolder.resetRequestAttributes()
        every { credentials.create(false, input) } returns created
        every { credentials.regenerate("id", false) } returns created

        assertSame(created, endpoint.create(false, input))
        assertSame(created, endpoint.regenerate("id", false))
    }
}
