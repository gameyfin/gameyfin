package org.gameyfin.app.users.entities

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserEntityTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    private fun oidcUser(
        subject: String = "sub-1",
        preferredUsername: String? = "alice",
        email: String = "alice@example.com"
    ): OidcUser = mockk {
        every { this@mockk.subject } returns subject
        every { this@mockk.preferredUsername } returns preferredUsername
        every { this@mockk.email } returns email
    }

    // ── constructor(oidcUser) ─────────────────────────────────────────────────

    @Test
    fun `single-arg OidcUser constructor maps all fields`() {
        val oidcUser = oidcUser(subject = "sub-1", preferredUsername = "alice", email = "alice@example.com")

        val user = User(oidcUser)

        assertEquals("alice", user.username)
        assertEquals("alice@example.com", user.email)
        assertEquals("sub-1", user.oidcProviderId)
        assertTrue(user.emailConfirmed)
        assertTrue(user.enabled)
        assertNull(user.password)
    }

    // ── constructor(oidcUser, resolvedUsername) ───────────────────────────────

    @Test
    fun `two-arg OidcUser constructor uses provided resolvedUsername`() {
        val oidcUser = oidcUser(subject = "sub-2", preferredUsername = "original", email = "bob@example.com")

        val user = User(oidcUser, "resolved-name")

        assertEquals("resolved-name", user.username)
        assertEquals("bob@example.com", user.email)
        assertEquals("sub-2", user.oidcProviderId)
        assertTrue(user.emailConfirmed)
        assertTrue(user.enabled)
        assertNull(user.password)
    }

    @Test
    fun `two-arg constructor uses resolved name even when preferred_username is present`() {
        val oidcUser = oidcUser(preferredUsername = "preferred", email = "c@example.com")

        val user = User(oidcUser, "fallback-from-name-claim")

        assertEquals("fallback-from-name-claim", user.username)
    }

    @Test
    fun `two-arg constructor accepts sub as resolvedUsername when all claims are absent`() {
        val oidcUser = oidcUser(subject = "sub-fallback", preferredUsername = null)

        val user = User(oidcUser, "sub-fallback")

        assertEquals("sub-fallback", user.username)
        assertEquals("sub-fallback", user.oidcProviderId)
    }

    @Test
    fun `two-arg constructor correctly sets email and oidcProviderId regardless of username`() {
        val oidcUser = oidcUser(subject = "sub-xyz", email = "test@example.com")

        val user = User(oidcUser, "any-username")

        assertEquals("test@example.com", user.email)
        assertEquals("sub-xyz", user.oidcProviderId)
    }

    @Test
    fun `two-arg constructor sets emailConfirmed and enabled to true`() {
        val oidcUser = oidcUser()

        val user = User(oidcUser, "whatever")

        assertTrue(user.emailConfirmed)
        assertTrue(user.enabled)
    }
}


