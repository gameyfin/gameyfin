package org.gameyfin.app.core.security

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import kotlin.test.assertEquals

class OidcUserExtensionsTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    private fun oidcUser(
        preferredUsername: String? = null,
        name: String? = null,
        email: String? = null,
        subject: String = "sub-12345"
    ): OidcUser = mockk {
        every { getClaim<String>("preferred_username") } returns preferredUsername
        every { getClaim<String>("name") } returns name
        every { getClaim<String>("email") } returns email
        // support any other claim key by returning null
        every { getClaim<String>(not(or(or("preferred_username", "name"), "email"))) } returns null
        every { this@mockk.subject } returns subject
    }

    // ── configured attribute present ──────────────────────────────────────────

    @Test
    fun `returns configured attribute when it is present`() {
        val user = mockk<OidcUser> {
            every { getClaim<String>("upn") } returns "alice@corp"
            every { subject } returns "sub-1"
        }
        assertEquals("alice@corp", user.resolvedUsername("upn"))
    }

    @Test
    fun `returns preferred_username when it is configured and present`() {
        val user = oidcUser(preferredUsername = "alice")
        assertEquals("alice", user.resolvedUsername("preferred_username"))
    }

    // ── configured attribute absent, fallback chain ───────────────────────────

    @Test
    fun `falls back to preferred_username when configured attribute is absent`() {
        val user = mockk<OidcUser> {
            every { getClaim<String>("upn") } returns null
            every { getClaim<String>("preferred_username") } returns "alice"
            every { subject } returns "sub-1"
        }
        assertEquals("alice", user.resolvedUsername("upn"))
    }

    @Test
    fun `falls back to name when preferred_username is absent`() {
        val user = oidcUser(preferredUsername = null, name = "Alice Smith")
        assertEquals("Alice Smith", user.resolvedUsername("preferred_username"))
    }

    @Test
    fun `falls back to email when preferred_username and name are absent`() {
        val user = oidcUser(preferredUsername = null, name = null, email = "alice@example.com")
        assertEquals("alice@example.com", user.resolvedUsername("preferred_username"))
    }

    @Test
    fun `falls back to sub when all other claims are absent`() {
        val user = oidcUser(preferredUsername = null, name = null, email = null, subject = "sub-99")
        assertEquals("sub-99", user.resolvedUsername("preferred_username"))
    }

    // ── blank / empty strings are treated as absent ───────────────────────────

    @Test
    fun `skips blank preferred_username and falls back to name`() {
        val user = oidcUser(preferredUsername = "   ", name = "Bob")
        assertEquals("Bob", user.resolvedUsername("preferred_username"))
    }

    @Test
    fun `skips empty preferred_username and falls back to name`() {
        val user = oidcUser(preferredUsername = "", name = "Bob")
        assertEquals("Bob", user.resolvedUsername("preferred_username"))
    }

    @Test
    fun `skips blank name and falls back to email`() {
        val user = oidcUser(preferredUsername = null, name = "", email = "bob@example.com")
        assertEquals("bob@example.com", user.resolvedUsername("preferred_username"))
    }

    // ── default parameter ─────────────────────────────────────────────────────

    @Test
    fun `uses preferred_username by default when no attributeName argument is supplied`() {
        val user = oidcUser(preferredUsername = "charlie")
        assertEquals("charlie", user.resolvedUsername())
    }

    @Test
    fun `falls back to sub by default when all standard claims are absent`() {
        val user = oidcUser(subject = "sub-default-fallback")
        assertEquals("sub-default-fallback", user.resolvedUsername())
    }

    // ── configured attribute equals a fallback name ───────────────────────────

    @Test
    fun `does not duplicate lookup when configured attribute is preferred_username`() {
        val user = oidcUser(preferredUsername = "dave")
        // should still work correctly without doubling up
        assertEquals("dave", user.resolvedUsername("preferred_username"))
    }

    @Test
    fun `configured attribute email returns email claim directly`() {
        // When "email" is explicitly configured as the attribute, it should be tried first
        val mockUser = mockk<OidcUser> {
            every { getClaim<String>("email") } returns "eve@example.com"
            every { getClaim<String>("preferred_username") } returns "other"
            every { getClaim<String>("name") } returns null
            every { subject } returns "sub-3"
        }
        assertEquals("eve@example.com", mockUser.resolvedUsername("email"))
    }
}


