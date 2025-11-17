package org.gameyfin.app.core.token

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

class TokenTypeTest {

    @Test
    fun `PasswordReset should have correct key and expiration`() {
        val type = TokenType.PasswordReset

        assertEquals("password-reset", type.key)
        assertEquals(15, type.expiration.inWholeMinutes)
        assertTrue(type.expiration.isFinite())
    }

    @Test
    fun `EmailConfirmation should have correct key and infinite expiration`() {
        val type = TokenType.EmailConfirmation

        assertEquals("email-verification", type.key)
        assertEquals(Duration.INFINITE, type.expiration)
        assertTrue(type.expiration.isInfinite())
    }

    @Test
    fun `Invitation should have correct key and infinite expiration`() {
        val type = TokenType.Invitation

        assertEquals("invitation", type.key)
        assertEquals(Duration.INFINITE, type.expiration)
        assertTrue(type.expiration.isInfinite())
    }

    @Test
    fun `all token types should have unique keys`() {
        val types = listOf(
            TokenType.PasswordReset,
            TokenType.EmailConfirmation,
            TokenType.Invitation
        )

        val keys = types.map { it.key }.toSet()
        assertEquals(types.size, keys.size)
    }

    @Test
    fun `PasswordReset expiration should be positive`() {
        val type = TokenType.PasswordReset

        assertTrue(type.expiration.isPositive())
    }
}

