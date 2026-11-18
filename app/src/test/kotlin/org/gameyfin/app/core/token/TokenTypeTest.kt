package org.gameyfin.app.core.token

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenTypeTest {

    @Test
    fun `PasswordReset should have correct key and expiration`() {
        val type = TokenType.PasswordReset

        assertEquals("password-reset", type.key)
        assertEquals(Duration.ofMinutes(15), type.expiration)
    }

    @Test
    fun `EmailConfirmation should have correct key and infinite expiration`() {
        val type = TokenType.EmailConfirmation

        assertEquals("email-verification", type.key)
        assertNull(type.expiration)
    }

    @Test
    fun `Invitation should have correct key and infinite expiration`() {
        val type = TokenType.Invitation

        assertEquals("invitation", type.key)
        assertNull(type.expiration)
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

        assertTrue(requireNotNull(type.expiration) > Duration.ZERO)
    }
}
