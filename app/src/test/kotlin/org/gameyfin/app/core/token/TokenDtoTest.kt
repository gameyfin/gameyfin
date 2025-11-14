package org.gameyfin.app.core.token

import org.gameyfin.app.core.Role
import org.gameyfin.app.users.entities.User
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TokenDtoTest {

    @Test
    fun `constructor should create DTO from token with finite expiration`() {
        val user = createTestUser(1L, "testuser")
        val createdOn = Instant.parse("2024-01-01T10:00:00Z")
        val token = Token(
            secret = "test-secret-123",
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = createdOn
        )

        val dto = TokenDto(token)

        assertEquals("test-secret-123", dto.secret)
        assertEquals("password-reset", dto.type)
        val expectedExpiry = createdOn.plus(15, ChronoUnit.MINUTES)
        assertEquals(expectedExpiry.toString(), dto.expiresAt)
    }

    @Test
    fun `constructor should create DTO from token with infinite expiration`() {
        val user = createTestUser(1L, "testuser")
        val createdOn = Instant.parse("2024-01-01T10:00:00Z")
        val token = Token(
            secret = "test-secret-456",
            type = TokenType.EmailConfirmation,
            creator = user,
            createdOn = createdOn
        )

        val dto = TokenDto(token)

        assertEquals("test-secret-456", dto.secret)
        assertEquals("email-verification", dto.type)
        assertEquals("never", dto.expiresAt)
    }

    @Test
    fun `constructor should create DTO from invitation token`() {
        val user = createTestUser(1L, "testuser")
        val createdOn = Instant.parse("2024-06-15T14:30:00Z")
        val token = Token(
            secret = "invitation-secret",
            type = TokenType.Invitation,
            creator = user,
            createdOn = createdOn
        )

        val dto = TokenDto(token)

        assertEquals("invitation-secret", dto.secret)
        assertEquals("invitation", dto.type)
        assertEquals("never", dto.expiresAt)
    }

    @Test
    fun `constructor should handle token with null createdOn for finite expiration`() {
        val user = createTestUser(1L, "testuser")
        val token = Token(
            secret = "test-secret",
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = null
        )

        val dto = TokenDto(token)

        assertEquals("test-secret", dto.secret)
        assertEquals("password-reset", dto.type)
        assertEquals(Instant.MIN.toString(), dto.expiresAt)
    }

    @Test
    fun `constructor should handle token with null createdOn for infinite expiration`() {
        val user = createTestUser(1L, "testuser")
        val token = Token(
            secret = "test-secret",
            type = TokenType.Invitation,
            creator = user,
            createdOn = null
        )

        val dto = TokenDto(token)

        assertEquals("test-secret", dto.secret)
        assertEquals("invitation", dto.type)
        assertEquals("never", dto.expiresAt)
    }

    @Test
    fun `primary constructor should create DTO with given values`() {
        val dto = TokenDto(
            secret = "manual-secret",
            type = "custom-type",
            expiresAt = "2024-12-31T23:59:59Z"
        )

        assertEquals("manual-secret", dto.secret)
        assertEquals("custom-type", dto.type)
        assertEquals("2024-12-31T23:59:59Z", dto.expiresAt)
    }

    @Test
    fun `constructor should handle token with payload`() {
        val user = createTestUser(1L, "testuser")
        val payload = mapOf("key" to "value", "another" to "data")
        val token = Token(
            secret = "payload-secret",
            type = TokenType.Invitation,
            creator = user,
            payload = payload,
            createdOn = Instant.now()
        )

        val dto = TokenDto(token)

        assertEquals("payload-secret", dto.secret)
        assertEquals("invitation", dto.type)
        assertNotNull(dto.expiresAt)
    }

    @Test
    fun `constructor should handle token created at epoch`() {
        val user = createTestUser(1L, "testuser")
        val token = Token(
            secret = "epoch-secret",
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = Instant.EPOCH
        )

        val dto = TokenDto(token)

        assertEquals("epoch-secret", dto.secret)
        val expectedExpiry = Instant.EPOCH.plus(15, ChronoUnit.MINUTES)
        assertEquals(expectedExpiry.toString(), dto.expiresAt)
    }

    @Test
    fun `constructor should handle token created far in the future`() {
        val user = createTestUser(1L, "testuser")
        val futureTime = Instant.parse("2099-12-31T23:59:59Z")
        val token = Token(
            secret = "future-secret",
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = futureTime
        )

        val dto = TokenDto(token)

        assertEquals("future-secret", dto.secret)
        val expectedExpiry = futureTime.plus(15, ChronoUnit.MINUTES)
        assertEquals(expectedExpiry.toString(), dto.expiresAt)
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

