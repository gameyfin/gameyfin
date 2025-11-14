package org.gameyfin.app.messages

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageEndpointTest {

    private lateinit var messageService: MessageService
    private lateinit var messageEndpoint: MessageEndpoint

    @BeforeEach
    fun setup() {
        messageService = mockk()
        messageEndpoint = MessageEndpoint(messageService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `isEnabled should return true when message service is enabled`() {
        every { messageService.enabled } returns true

        val result = messageEndpoint.isEnabled()

        assertTrue(result)
        verify(exactly = 1) { messageService.enabled }
    }

    @Test
    fun `isEnabled should return false when message service is disabled`() {
        every { messageService.enabled } returns false

        val result = messageEndpoint.isEnabled()

        assertFalse(result)
        verify(exactly = 1) { messageService.enabled }
    }

    @Test
    fun `verifyCredentials should delegate to messageService and return true on success`() {
        val provider = "email"
        val credentials = mapOf("host" to "smtp.example.com", "port" to 587)

        every { messageService.testCredentials(provider, credentials) } returns true

        val result = messageEndpoint.verifyCredentials(provider, credentials)

        assertTrue(result)
        verify(exactly = 1) { messageService.testCredentials(provider, credentials) }
    }

    @Test
    fun `verifyCredentials should delegate to messageService and return false on failure`() {
        val provider = "email"
        val credentials = mapOf("host" to "smtp.example.com", "port" to 587)

        every { messageService.testCredentials(provider, credentials) } returns false

        val result = messageEndpoint.verifyCredentials(provider, credentials)

        assertFalse(result)
        verify(exactly = 1) { messageService.testCredentials(provider, credentials) }
    }

    @Test
    fun `verifyCredentials should handle empty credentials map`() {
        val provider = "email"
        val credentials = emptyMap<String, Any>()

        every { messageService.testCredentials(provider, credentials) } returns false

        val result = messageEndpoint.verifyCredentials(provider, credentials)

        assertFalse(result)
        verify(exactly = 1) { messageService.testCredentials(provider, credentials) }
    }

    @Test
    fun `verifyCredentials should handle complex credentials with nested structures`() {
        val provider = "email"
        val credentials = mapOf(
            "host" to "smtp.example.com",
            "port" to 587,
            "username" to "user@example.com",
            "password" to "secret"
        )

        every { messageService.testCredentials(provider, credentials) } returns true

        val result = messageEndpoint.verifyCredentials(provider, credentials)

        assertTrue(result)
        verify(exactly = 1) { messageService.testCredentials(provider, credentials) }
    }

    @Test
    fun `sendTestNotification should delegate to messageService and return true on success`() {
        val templateKey = "password-reset-request"
        val placeholders = mapOf("username" to "testuser", "resetLink" to "http://example.com/reset")

        every { messageService.sendTestNotification(templateKey, placeholders) } returns true

        val result = messageEndpoint.sendTestNotification(templateKey, placeholders)

        assertTrue(result)
        verify(exactly = 1) { messageService.sendTestNotification(templateKey, placeholders) }
    }

    @Test
    fun `sendTestNotification should delegate to messageService and return false on failure`() {
        val templateKey = "password-reset-request"
        val placeholders = mapOf("username" to "testuser", "resetLink" to "http://example.com/reset")

        every { messageService.sendTestNotification(templateKey, placeholders) } returns false

        val result = messageEndpoint.sendTestNotification(templateKey, placeholders)

        assertFalse(result)
        verify(exactly = 1) { messageService.sendTestNotification(templateKey, placeholders) }
    }

    @Test
    fun `sendTestNotification should handle empty placeholders`() {
        val templateKey = "waiting-for-approval"
        val placeholders = emptyMap<String, String>()

        every { messageService.sendTestNotification(templateKey, placeholders) } returns true

        val result = messageEndpoint.sendTestNotification(templateKey, placeholders)

        assertTrue(result)
        verify(exactly = 1) { messageService.sendTestNotification(templateKey, placeholders) }
    }

    @Test
    fun `sendTestNotification should handle multiple placeholders`() {
        val templateKey = "email-already-registered"
        val placeholders = mapOf(
            "username" to "testuser",
            "passwordResetLink" to "http://example.com/reset"
        )

        every { messageService.sendTestNotification(templateKey, placeholders) } returns true

        val result = messageEndpoint.sendTestNotification(templateKey, placeholders)

        assertTrue(result)
        verify(exactly = 1) { messageService.sendTestNotification(templateKey, placeholders) }
    }
}

