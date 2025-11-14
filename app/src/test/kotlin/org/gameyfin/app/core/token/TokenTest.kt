package org.gameyfin.app.core.token

import org.gameyfin.app.core.Role
import org.gameyfin.app.users.entities.User
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenTest {

    @Test
    fun `expired should return false when token type has infinite expiration`() {
        val user = createTestUser(1L, "testuser")
        val token = Token(
            secret = "test-secret",
            type = TokenType.EmailConfirmation,
            creator = user,
            createdOn = Instant.now().minus(365, ChronoUnit.DAYS)
        )

        assertFalse(token.expired)
    }

    @Test
    fun `expired should return false when token is not yet expired`() {
        val user = createTestUser(1L, "testuser")
        val token = Token(
            secret = "test-secret",
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = Instant.now().minus(5, ChronoUnit.MINUTES)
        )

        assertFalse(token.expired)
    }

    @Test
    fun `expired should return true when token has expired`() {
        val user = createTestUser(1L, "testuser")
        val token = Token(
            secret = "test-secret",
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = Instant.now().minus(20, ChronoUnit.MINUTES)
        )

        assertTrue(token.expired)
    }

    @Test
    fun `expired should return false when token expires exactly now`() {
        val user = createTestUser(1L, "testuser")
        val now = Instant.now()
        val token = Token(
            secret = "test-secret",
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = now.minus(15, ChronoUnit.MINUTES)
        )

        assertFalse(token.expired)
    }

    @Test
    fun `expired should handle tokens created just now`() {
        val user = createTestUser(1L, "testuser")
        val token = Token(
            secret = "test-secret",
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = Instant.now()
        )

        assertFalse(token.expired)
    }

    @Test
    fun `token should be created with empty payload by default`() {
        val user = createTestUser(1L, "testuser")
        val token = Token(
            secret = "test-secret",
            type = TokenType.EmailConfirmation,
            creator = user
        )

        assertTrue(token.payload.isEmpty())
    }

    @Test
    fun `token should support custom payload`() {
        val user = createTestUser(1L, "testuser")
        val payload = mapOf("key1" to "value1", "key2" to "value2")
        val token = Token(
            secret = "test-secret",
            type = TokenType.Invitation,
            creator = user,
            payload = payload
        )

        assertTrue(token.payload.isNotEmpty())
        assertTrue(token.payload.containsKey("key1"))
        assertTrue(token.payload.containsKey("key2"))
    }

    @Test
    fun `expired should return false for invitation token created long ago`() {
        val user = createTestUser(1L, "testuser")
        val token = Token(
            secret = "test-secret",
            type = TokenType.Invitation,
            creator = user,
            createdOn = Instant.now().minus(365, ChronoUnit.DAYS)
        )

        assertFalse(token.expired)
    }

    @Test
    fun `expired should handle edge case of createdOn at epoch`() {
        val user = createTestUser(1L, "testuser")
        val token = Token(
            secret = "test-secret",
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = Instant.EPOCH
        )

        assertTrue(token.expired)
    }

    private fun createTestUser(id: Long, username: String): User {
        return User(
            id = id,
            username = username,
            email = "$username@example.com",
            password = "password",
            enabled = true,
            emailConfirmed = true,
            roles = listOf(Role.USER)
        )
    }
}

