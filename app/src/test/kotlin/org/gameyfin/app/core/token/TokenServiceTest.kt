package org.gameyfin.app.core.token

import io.mockk.*
import org.gameyfin.app.core.Role
import org.gameyfin.app.users.entities.User
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TokenServiceTest {

    private lateinit var tokenRepository: TokenRepository
    private lateinit var tokenService: TestTokenService

    @BeforeEach
    fun setup() {
        tokenRepository = mockk()
        tokenService = TestTokenService(TokenType.PasswordReset, tokenRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `generate should create and save new token`() {
        val user = createTestUser(1L, "testuser")
        val savedToken = Token(
            secret = "generated-secret",
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = Instant.now()
        )

        every { tokenRepository.findByCreatorAndType(user, TokenType.PasswordReset) } returns null
        every { tokenRepository.save(any<Token<TokenType.PasswordReset>>()) } returns savedToken

        val result = tokenService.generate(user)

        assertNotNull(result)
        assertEquals(user, result.creator)
        assertEquals(TokenType.PasswordReset, result.type)
        verify(exactly = 1) { tokenRepository.findByCreatorAndType(user, TokenType.PasswordReset) }
        verify(exactly = 1) { tokenRepository.save(any<Token<TokenType.PasswordReset>>()) }
    }

    @Test
    fun `generate should delete existing token before creating new one`() {
        val user = createTestUser(1L, "testuser")
        val existingToken = Token(
            secret = "old-secret",
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = Instant.now()
        )
        val newToken = Token(
            secret = "new-secret",
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = Instant.now()
        )

        every { tokenRepository.findByCreatorAndType(user, TokenType.PasswordReset) } returns existingToken
        every { tokenRepository.delete(existingToken) } just Runs
        every { tokenRepository.save(any<Token<TokenType.PasswordReset>>()) } returns newToken

        val result = tokenService.generate(user)

        assertNotNull(result)
        assertEquals("new-secret", result.secret)
        verify(exactly = 1) { tokenRepository.findByCreatorAndType(user, TokenType.PasswordReset) }
        verify(exactly = 1) { tokenRepository.delete(existingToken) }
        verify(exactly = 1) { tokenRepository.save(any<Token<TokenType.PasswordReset>>()) }
    }

    @Test
    fun `generateWithPayload should create and save token with payload`() {
        val user = createTestUser(1L, "testuser")
        val payload = mapOf("key1" to "value1", "key2" to "value2")
        val savedToken = Token(
            secret = "payload-secret",
            type = TokenType.Invitation,
            creator = user,
            payload = payload,
            createdOn = Instant.now()
        )

        val invitationService = TestTokenService(TokenType.Invitation, tokenRepository)
        every {
            tokenRepository.findByCreatorAndTypeAndPayload(
                user,
                TokenType.Invitation,
                payload
            )
        } returns null
        every { tokenRepository.save(any<Token<TokenType.Invitation>>()) } returns savedToken

        val result = invitationService.generateWithPayload(user, payload)

        assertNotNull(result)
        assertEquals(payload, result.payload)
        assertEquals(user, result.creator)
        verify(exactly = 1) {
            tokenRepository.findByCreatorAndTypeAndPayload(
                user,
                TokenType.Invitation,
                payload
            )
        }
        verify(exactly = 1) { tokenRepository.save(any<Token<TokenType.Invitation>>()) }
    }

    @Test
    fun `generateWithPayload should delete existing token with same payload`() {
        val user = createTestUser(1L, "testuser")
        val payload = mapOf("data" to "test")
        val existingToken = Token(
            secret = "old-payload-secret",
            type = TokenType.Invitation,
            creator = user,
            payload = payload,
            createdOn = Instant.now()
        )
        val newToken = Token(
            secret = "new-payload-secret",
            type = TokenType.Invitation,
            creator = user,
            payload = payload,
            createdOn = Instant.now()
        )

        val invitationService = TestTokenService(TokenType.Invitation, tokenRepository)
        every {
            tokenRepository.findByCreatorAndTypeAndPayload(
                user,
                TokenType.Invitation,
                payload
            )
        } returns existingToken
        every { tokenRepository.delete(existingToken) } just Runs
        every { tokenRepository.save(any<Token<TokenType.Invitation>>()) } returns newToken

        val result = invitationService.generateWithPayload(user, payload)

        assertNotNull(result)
        assertEquals("new-payload-secret", result.secret)
        verify(exactly = 1) { tokenRepository.delete(existingToken) }
        verify(exactly = 1) { tokenRepository.save(any<Token<TokenType.Invitation>>()) }
    }

    @Test
    fun `generateWithPayload should handle empty payload`() {
        val user = createTestUser(1L, "testuser")
        val emptyPayload = emptyMap<String, String>()
        val savedToken = Token(
            secret = "empty-payload-secret",
            type = TokenType.Invitation,
            creator = user,
            payload = emptyPayload,
            createdOn = Instant.now()
        )

        val invitationService = TestTokenService(TokenType.Invitation, tokenRepository)
        every {
            tokenRepository.findByCreatorAndTypeAndPayload(
                user,
                TokenType.Invitation,
                emptyPayload
            )
        } returns null
        every { tokenRepository.save(any<Token<TokenType.Invitation>>()) } returns savedToken

        val result = invitationService.generateWithPayload(user, emptyPayload)

        assertNotNull(result)
        assertEquals(emptyMap(), result.payload)
    }

    @Test
    fun `get should return token when found with matching type`() {
        val user = createTestUser(1L, "testuser")
        val secret = "test-secret"
        val token = Token(
            secret = secret,
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = Instant.now()
        )

        every { tokenRepository.findBySecret(secret) } returns token

        val result = tokenService.get(secret, TokenType.PasswordReset)

        assertNotNull(result)
        assertEquals(secret, result.secret)
        assertEquals(TokenType.PasswordReset, result.type)
        verify(exactly = 1) { tokenRepository.findBySecret(secret) }
    }

    @Test
    fun `get should return null when token not found`() {
        val secret = "non-existent-secret"

        every { tokenRepository.findBySecret(secret) } returns null

        val result = tokenService.get(secret, TokenType.PasswordReset)

        assertNull(result)
        verify(exactly = 1) { tokenRepository.findBySecret(secret) }
    }

    @Test
    fun `get should return null when token type does not match`() {
        val user = createTestUser(1L, "testuser")
        val secret = "test-secret"
        val token = Token(
            secret = secret,
            type = TokenType.EmailConfirmation,
            creator = user,
            createdOn = Instant.now()
        )

        every { tokenRepository.findBySecret(secret) } returns token

        val result = tokenService.get(secret, TokenType.PasswordReset)

        assertNull(result)
        verify(exactly = 1) { tokenRepository.findBySecret(secret) }
    }

    @Test
    fun `getPayload should return payload when token exists`() {
        val user = createTestUser(1L, "testuser")
        val secret = "test-secret"
        val payload = mapOf("key" to "value", "foo" to "bar")
        val token = Token(
            secret = secret,
            type = TokenType.Invitation,
            creator = user,
            payload = payload,
            createdOn = Instant.now()
        )

        every { tokenRepository.findBySecret(secret) } returns token

        val result = tokenService.getPayload(secret)

        assertNotNull(result)
        assertEquals(payload, result)
        assertEquals("value", result["key"])
        assertEquals("bar", result["foo"])
        verify(exactly = 1) { tokenRepository.findBySecret(secret) }
    }

    @Test
    fun `getPayload should return null when token not found`() {
        val secret = "non-existent-secret"

        every { tokenRepository.findBySecret(secret) } returns null

        val result = tokenService.getPayload(secret)

        assertNull(result)
        verify(exactly = 1) { tokenRepository.findBySecret(secret) }
    }

    @Test
    fun `getPayload should return empty map when token has no payload`() {
        val user = createTestUser(1L, "testuser")
        val secret = "test-secret"
        val token = Token(
            secret = secret,
            type = TokenType.PasswordReset,
            creator = user,
            payload = emptyMap(),
            createdOn = Instant.now()
        )

        every { tokenRepository.findBySecret(secret) } returns token

        val result = tokenService.getPayload(secret)

        assertNotNull(result)
        assertEquals(emptyMap(), result)
        verify(exactly = 1) { tokenRepository.findBySecret(secret) }
    }

    @Test
    fun `delete should remove token from repository`() {
        val user = createTestUser(1L, "testuser")
        val token: Token<TokenType> = Token(
            secret = "test-secret",
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = Instant.now()
        )

        every { tokenRepository.delete(token) } just Runs

        tokenService.delete(token)

        verify(exactly = 1) { tokenRepository.delete(token) }
    }

    @Test
    fun `delete should handle exception when token already deleted`() {
        val user = createTestUser(1L, "testuser")
        val token: Token<TokenType> = Token(
            secret = "test-secret",
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = Instant.now()
        )

        every { tokenRepository.delete(token) } throws RuntimeException("Token not found")

        tokenService.delete(token)

        verify(exactly = 1) { tokenRepository.delete(token) }
    }

    @Test
    fun `validate should return VALID when token exists and not expired`() {
        val user = createTestUser(1L, "testuser")
        val secret = "valid-secret"
        val token = Token(
            secret = secret,
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = Instant.now().minus(5, ChronoUnit.MINUTES)
        )

        every { tokenRepository.findBySecret(secret) } returns token

        val result = tokenService.validate(secret)

        assertEquals(TokenValidationResult.VALID, result)
        verify(exactly = 1) { tokenRepository.findBySecret(secret) }
    }

    @Test
    fun `validate should return INVALID when token not found`() {
        val secret = "non-existent-secret"

        every { tokenRepository.findBySecret(secret) } returns null

        val result = tokenService.validate(secret)

        assertEquals(TokenValidationResult.INVALID, result)
        verify(exactly = 1) { tokenRepository.findBySecret(secret) }
    }

    @Test
    fun `validate should return EXPIRED when token has expired`() {
        val user = createTestUser(1L, "testuser")
        val secret = "expired-secret"
        val token = Token(
            secret = secret,
            type = TokenType.PasswordReset,
            creator = user,
            createdOn = Instant.now().minus(20, ChronoUnit.MINUTES)
        )

        every { tokenRepository.findBySecret(secret) } returns token

        val result = tokenService.validate(secret)

        assertEquals(TokenValidationResult.EXPIRED, result)
        verify(exactly = 1) { tokenRepository.findBySecret(secret) }
    }

    @Test
    fun `validate should return VALID for token with infinite expiration`() {
        val user = createTestUser(1L, "testuser")
        val secret = "infinite-secret"
        val token = Token(
            secret = secret,
            type = TokenType.EmailConfirmation,
            creator = user,
            createdOn = Instant.now().minus(365, ChronoUnit.DAYS)
        )

        every { tokenRepository.findBySecret(secret) } returns token

        val result = tokenService.validate(secret)

        assertEquals(TokenValidationResult.VALID, result)
        verify(exactly = 1) { tokenRepository.findBySecret(secret) }
    }

    @Test
    fun `generate should work for different token types`() {
        val user = createTestUser(1L, "testuser")
        val emailService = TestTokenService(TokenType.EmailConfirmation, tokenRepository)
        val savedToken = Token(
            secret = "email-secret",
            type = TokenType.EmailConfirmation,
            creator = user,
            createdOn = Instant.now()
        )

        every { tokenRepository.findByCreatorAndType(user, TokenType.EmailConfirmation) } returns null
        every { tokenRepository.save(any<Token<TokenType.EmailConfirmation>>()) } returns savedToken

        val result = emailService.generate(user)

        assertNotNull(result)
        assertEquals(TokenType.EmailConfirmation, result.type)
        verify(exactly = 1) { tokenRepository.save(any<Token<TokenType.EmailConfirmation>>()) }
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

    private class TestTokenService(
        type: TokenType,
        tokenRepository: TokenRepository
    ) : TokenService<TokenType>(type, tokenRepository)
}

