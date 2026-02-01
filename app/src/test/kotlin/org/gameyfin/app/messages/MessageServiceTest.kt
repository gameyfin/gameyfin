package org.gameyfin.app.messages

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.gameyfin.app.core.events.*
import org.gameyfin.app.core.token.Token
import org.gameyfin.app.core.token.TokenType
import org.gameyfin.app.messages.providers.AbstractMessageProvider
import org.gameyfin.app.messages.templates.MessageTemplateService
import org.gameyfin.app.messages.templates.MessageTemplates
import org.gameyfin.app.messages.templates.TemplateType
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.entities.User
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBeansOfType
import org.springframework.context.ApplicationContext
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageServiceTest {

    private lateinit var applicationContext: ApplicationContext
    private lateinit var templateService: MessageTemplateService
    private lateinit var userService: UserService
    private lateinit var messageService: MessageService
    private lateinit var mockProvider1: AbstractMessageProvider
    private lateinit var mockProvider2: AbstractMessageProvider

    @BeforeEach
    fun setup() {
        applicationContext = mockk()
        templateService = mockk()
        userService = mockk()
        mockProvider1 = mockk(relaxed = true)
        mockProvider2 = mockk(relaxed = true)

        messageService = MessageService(applicationContext, templateService, userService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `enabled should return true when at least one provider is enabled`() {
        every { mockProvider1.enabled } returns true
        every { mockProvider2.enabled } returns false
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1,
            "provider2" to mockProvider2
        )

        val result = messageService.enabled

        assertTrue(result)
    }

    @Test
    fun `enabled should return false when no providers are enabled`() {
        every { mockProvider1.enabled } returns false
        every { mockProvider2.enabled } returns false
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1,
            "provider2" to mockProvider2
        )

        val result = messageService.enabled

        assertFalse(result)
    }

    @Test
    fun `enabled should return false when no providers exist`() {
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns emptyMap()

        val result = messageService.enabled

        assertFalse(result)
    }

    @Test
    fun `testCredentials should return true when provider validates credentials successfully`() {
        val providerKey = "email"
        val credentials = mapOf("host" to "smtp.example.com", "port" to 587)

        every { mockProvider1.providerKey } returns providerKey
        every { mockProvider1.testCredentials(any()) } returns true
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1
        )

        val result = messageService.testCredentials(providerKey, credentials)

        assertTrue(result)
        verify { mockProvider1.testCredentials(match { it["host"] == "smtp.example.com" && it["port"] == 587 }) }
    }

    @Test
    fun `testCredentials should return false when provider validation fails`() {
        val providerKey = "email"
        val credentials = mapOf("host" to "smtp.example.com", "port" to 587)

        every { mockProvider1.providerKey } returns providerKey
        every { mockProvider1.testCredentials(any()) } returns false
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1
        )

        val result = messageService.testCredentials(providerKey, credentials)

        assertFalse(result)
    }

    @Test
    fun `testCredentials should throw exception when provider not found`() {
        val providerKey = "nonexistent"
        val credentials = mapOf("host" to "smtp.example.com")

        every { mockProvider1.providerKey } returns "email"
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            messageService.testCredentials(providerKey, credentials)
        }

        assertEquals("Provider 'nonexistent' not found", exception.message)
    }

    @Test
    fun `testCredentials should handle empty credentials`() {
        val providerKey = "email"
        val credentials = emptyMap<String, Any>()

        every { mockProvider1.providerKey } returns providerKey
        every { mockProvider1.testCredentials(any()) } returns true
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1
        )

        val result = messageService.testCredentials(providerKey, credentials)

        assertTrue(result)
        verify { mockProvider1.testCredentials(match { it.isEmpty() }) }
    }

    @Test
    fun `sendNotification should send to all enabled providers`() {
        val recipient = "user@example.com"
        val title = "Test Title"
        val template = MessageTemplates.PasswordResetRequest
        val placeholders = mapOf("username" to "testuser", "resetLink" to "http://example.com/reset")
        val filledContent = "<html>Password reset</html>"

        every { mockProvider1.enabled } returns true
        every { mockProvider1.supportedTemplateType } returns TemplateType.MJML
        every { mockProvider2.enabled } returns true
        every { mockProvider2.supportedTemplateType } returns TemplateType.TEXT
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1,
            "provider2" to mockProvider2
        )
        every { templateService.fillMessageTemplate(template, TemplateType.MJML, placeholders) } returns filledContent
        every {
            templateService.fillMessageTemplate(
                template,
                TemplateType.TEXT,
                placeholders
            )
        } returns "Password reset"

        messageService.sendNotification(recipient, title, template, placeholders)

        verify { mockProvider1.sendNotification(recipient, title, filledContent) }
        verify { mockProvider2.sendNotification(recipient, title, "Password reset") }
    }

    @Test
    fun `sendNotification should only send to enabled providers`() {
        val recipient = "user@example.com"
        val title = "Test Title"
        val template = MessageTemplates.WaitingForApproval
        val placeholders = mapOf("username" to "testuser")
        val filledContent = "<html>Waiting</html>"

        every { mockProvider1.enabled } returns true
        every { mockProvider1.supportedTemplateType } returns TemplateType.MJML
        every { mockProvider2.enabled } returns false
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1,
            "provider2" to mockProvider2
        )
        every { templateService.fillMessageTemplate(template, TemplateType.MJML, placeholders) } returns filledContent

        messageService.sendNotification(recipient, title, template, placeholders)

        verify { mockProvider1.sendNotification(recipient, title, filledContent) }
        verify(exactly = 0) { mockProvider2.sendNotification(any(), any(), any()) }
    }

    @Test
    fun `sendNotification should not send when no providers are enabled`() {
        val recipient = "user@example.com"
        val title = "Test Title"
        val template = MessageTemplates.AccountEnabled
        val placeholders = mapOf("username" to "testuser", "baseUrl" to "http://example.com")

        every { mockProvider1.enabled } returns false
        every { mockProvider2.enabled } returns false
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1,
            "provider2" to mockProvider2
        )

        messageService.sendNotification(recipient, title, template, placeholders)

        verify(exactly = 0) { mockProvider1.sendNotification(any(), any(), any()) }
        verify(exactly = 0) { mockProvider2.sendNotification(any(), any(), any()) }
    }

    @Test
    fun `sendTestNotification should return false when messaging is disabled`() {
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns emptyMap()

        val result = messageService.sendTestNotification("password-reset-request", emptyMap())

        assertFalse(result)
    }

    @Test
    fun `sendTestNotification should send notification to current user and return true`() {
        val templateKey = "password-reset-request"
        val placeholders = mapOf("username" to "testuser", "resetLink" to "http://example.com/reset")
        val user = User(username = "testuser", email = "user@example.com", password = "hash")
        val filledContent = "<html>Password reset</html>"

        setupSecurityContext("testuser")

        every { mockProvider1.enabled } returns true
        every { mockProvider1.supportedTemplateType } returns TemplateType.MJML
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1
        )
        every { userService.getByUsername("testuser") } returns user
        every { templateService.getMessageTemplate(templateKey) } returns MessageTemplates.PasswordResetRequest
        every {
            templateService.fillMessageTemplate(
                MessageTemplates.PasswordResetRequest,
                TemplateType.MJML,
                placeholders
            )
        } returns filledContent

        val result = messageService.sendTestNotification(templateKey, placeholders)

        assertTrue(result)
        verify { mockProvider1.sendNotification("user@example.com", "[Gameyfin] Test Notification", filledContent) }
    }

    @Test
    fun `sendTestNotification should return false when no authentication found`() {
        val templateKey = "password-reset-request"
        val placeholders = mapOf("username" to "testuser", "resetLink" to "http://example.com/reset")

        every { mockProvider1.enabled } returns true
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1
        )

        val result = messageService.sendTestNotification(templateKey, placeholders)

        assertFalse(result)
    }

    @Test
    fun `sendTestNotification should return false when user not found`() {
        val templateKey = "password-reset-request"
        val placeholders = mapOf("username" to "testuser", "resetLink" to "http://example.com/reset")

        setupSecurityContext("testuser")

        every { mockProvider1.enabled } returns true
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1
        )
        every { userService.getByUsername("testuser") } returns null

        val result = messageService.sendTestNotification(templateKey, placeholders)

        assertFalse(result)
    }

    @Test
    fun `sendTestNotification should return false on exception`() {
        val templateKey = "password-reset-request"
        val placeholders = mapOf("username" to "testuser", "resetLink" to "http://example.com/reset")
        val user = User(username = "testuser", email = "user@example.com", password = "hash")

        setupSecurityContext("testuser")

        every { mockProvider1.enabled } returns true
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1
        )
        every { userService.getByUsername("testuser") } returns user
        every { templateService.getMessageTemplate(templateKey) } throws RuntimeException("Template not found")

        val result = messageService.sendTestNotification(templateKey, placeholders)

        assertFalse(result)
    }

    @Test
    fun `onPasswordResetRequest should send notification when enabled`() {
        val user = User(username = "testuser", email = "user@example.com", password = "hash")
        val token = Token(creator = user, secret = "secret123", type = TokenType.PasswordReset)
        val event = PasswordResetRequestEvent(this, token, "http://example.com")
        val filledContent = "<html>Reset password</html>"

        every { mockProvider1.enabled } returns true
        every { mockProvider1.supportedTemplateType } returns TemplateType.MJML
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1
        )
        every {
            templateService.fillMessageTemplate(
                MessageTemplates.PasswordResetRequest,
                TemplateType.MJML,
                mapOf("username" to "testuser", "resetLink" to "http://example.com/reset-password?token=secret123")
            )
        } returns filledContent

        messageService.onPasswordResetRequest(event)

        verify {
            mockProvider1.sendNotification(
                "user@example.com",
                "[Gameyfin] Password Reset Request",
                filledContent
            )
        }
    }

    @Test
    fun `onPasswordResetRequest should not send when disabled`() {
        val user = User(username = "testuser", email = "user@example.com", password = "hash")
        val token = Token(creator = user, secret = "secret123", type = TokenType.PasswordReset)
        val event = PasswordResetRequestEvent(this, token, "http://example.com")

        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns emptyMap()

        messageService.onPasswordResetRequest(event)

        verify(exactly = 0) { mockProvider1.sendNotification(any(), any(), any()) }
    }

    @Test
    fun `onUserRegistrationWaitingForApproval should send notification when enabled`() {
        val user = User(username = "newuser", email = "new@example.com", password = "hash")
        val event = UserRegistrationWaitingForApprovalEvent(this, user)
        val filledContent = "<html>Waiting for approval</html>"

        every { mockProvider1.enabled } returns true
        every { mockProvider1.supportedTemplateType } returns TemplateType.MJML
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1
        )
        every {
            templateService.fillMessageTemplate(
                MessageTemplates.WaitingForApproval,
                TemplateType.MJML,
                mapOf("username" to "newuser")
            )
        } returns filledContent

        messageService.onUserRegistrationWaitingForApproval(event)

        verify { mockProvider1.sendNotification("new@example.com", "[Gameyfin] Waiting for Approval", filledContent) }
    }

    @Test
    fun `onUserRegistrationWaitingForApproval should not send when disabled`() {
        val user = User(username = "newuser", email = "new@example.com", password = "hash")
        val event = UserRegistrationWaitingForApprovalEvent(this, user)

        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns emptyMap()

        messageService.onUserRegistrationWaitingForApproval(event)

        verify(exactly = 0) { mockProvider1.sendNotification(any(), any(), any()) }
    }

    @Test
    fun `onAccountStatusChanged should send enabled notification when user is enabled`() {
        val user = User(username = "testuser", email = "user@example.com", password = "hash", enabled = true)
        val event = AccountStatusChangedEvent(this, user, "http://example.com")
        val filledContent = "<html>Account enabled</html>"

        every { mockProvider1.enabled } returns true
        every { mockProvider1.supportedTemplateType } returns TemplateType.MJML
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1
        )
        every {
            templateService.fillMessageTemplate(
                MessageTemplates.AccountEnabled,
                TemplateType.MJML,
                mapOf("username" to "testuser", "baseUrl" to "http://example.com")
            )
        } returns filledContent

        messageService.onAccountStatusChanged(event)

        verify {
            mockProvider1.sendNotification(
                "user@example.com",
                "[Gameyfin] Your account has been enabled",
                filledContent
            )
        }
    }

    @Test
    fun `onAccountStatusChanged should send disabled notification when user is disabled`() {
        val user = User(username = "testuser", email = "user@example.com", password = "hash", enabled = false)
        val event = AccountStatusChangedEvent(this, user, "http://example.com")
        val filledContent = "<html>Account disabled</html>"

        every { mockProvider1.enabled } returns true
        every { mockProvider1.supportedTemplateType } returns TemplateType.MJML
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1
        )
        every {
            templateService.fillMessageTemplate(
                MessageTemplates.AccountDisabled,
                TemplateType.MJML,
                mapOf("username" to "testuser", "baseUrl" to "http://example.com")
            )
        } returns filledContent

        messageService.onAccountStatusChanged(event)

        verify {
            mockProvider1.sendNotification(
                "user@example.com",
                "[Gameyfin] Your account has been disabled",
                filledContent
            )
        }
    }

    @Test
    fun `onAccountStatusChanged should not send when disabled`() {
        val user = User(username = "testuser", email = "user@example.com", password = "hash")
        val event = AccountStatusChangedEvent(this, user, "http://example.com")

        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns emptyMap()

        messageService.onAccountStatusChanged(event)

        verify(exactly = 0) { mockProvider1.sendNotification(any(), any(), any()) }
    }

    @Test
    fun `onRegistrationAttemptWithExistingEmail should send notification when enabled`() {
        val user = User(username = "existinguser", email = "existing@example.com", password = "hash")
        val event = RegistrationAttemptWithExistingEmailEvent(this, user, "http://example.com")
        val filledContent = "<html>Registration attempt</html>"

        every { mockProvider1.enabled } returns true
        every { mockProvider1.supportedTemplateType } returns TemplateType.MJML
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1
        )
        every {
            templateService.fillMessageTemplate(
                MessageTemplates.RegistrationAttemptWithExistingEmail,
                TemplateType.MJML,
                mapOf("username" to "existinguser", "passwordResetLink" to "http://example.com")
            )
        } returns filledContent

        messageService.onRegistrationAttemptWithExistingEmail(event)

        verify { mockProvider1.sendNotification("existing@example.com", "[Gameyfin] Account alert", filledContent) }
    }

    @Test
    fun `onRegistrationAttemptWithExistingEmail should not send when disabled`() {
        val user = User(username = "existinguser", email = "existing@example.com", password = "hash")
        val event = RegistrationAttemptWithExistingEmailEvent(this, user, "http://example.com")

        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns emptyMap()

        messageService.onRegistrationAttemptWithExistingEmail(event)

        verify(exactly = 0) { mockProvider1.sendNotification(any(), any(), any()) }
    }

    @Test
    fun `onEmailNeedsConfirmation should send notification when enabled`() {
        val user = User(username = "testuser", email = "user@example.com", password = "hash")
        val token = Token(creator = user, secret = "confirm123", type = TokenType.EmailConfirmation)
        val event = EmailNeedsConfirmationEvent(this, token, "http://example.com")
        val filledContent = "<html>Email confirmation</html>"

        every { mockProvider1.enabled } returns true
        every { mockProvider1.supportedTemplateType } returns TemplateType.MJML
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1
        )
        every {
            templateService.fillMessageTemplate(
                MessageTemplates.EmailConfirmation,
                TemplateType.MJML,
                mapOf(
                    "username" to "testuser",
                    "confirmationLink" to "http://example.com/confirm-email?token=confirm123"
                )
            )
        } returns filledContent

        messageService.onEmailNeedsConfirmation(event)

        verify { mockProvider1.sendNotification("user@example.com", "[Gameyfin] Email Confirmation", filledContent) }
    }

    @Test
    fun `onEmailNeedsConfirmation should not send when disabled`() {
        val user = User(username = "testuser", email = "user@example.com", password = "hash")
        val token = Token(creator = user, secret = "confirm123", type = TokenType.EmailConfirmation)
        val event = EmailNeedsConfirmationEvent(this, token, "http://example.com")

        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns emptyMap()

        messageService.onEmailNeedsConfirmation(event)

        verify(exactly = 0) { mockProvider1.sendNotification(any(), any(), any()) }
    }

    @Test
    fun `onUserInvitation should send notification when enabled`() {
        val user = User(username = "inviter", email = "inviter@example.com", password = "hash")
        val token = Token(creator = user, secret = "invite123", type = TokenType.Invitation)
        val event = UserInvitationEvent(this, token, "http://example.com", "invited@example.com")
        val filledContent = "<html>Invitation</html>"

        every { mockProvider1.enabled } returns true
        every { mockProvider1.supportedTemplateType } returns TemplateType.MJML
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1
        )
        every {
            templateService.fillMessageTemplate(
                MessageTemplates.UserInvitation,
                TemplateType.MJML,
                mapOf("invitationLink" to "http://example.com/accept-invitation?token=invite123")
            )
        } returns filledContent

        messageService.onUserInvitation(event)

        verify {
            mockProvider1.sendNotification(
                "invited@example.com",
                "[Gameyfin] You've been invited!",
                filledContent
            )
        }
    }

    @Test
    fun `onUserInvitation should not send when disabled`() {
        val user = User(username = "inviter", email = "inviter@example.com", password = "hash")
        val token = Token(creator = user, secret = "invite123", type = TokenType.Invitation)
        val event = UserInvitationEvent(this, token, "http://example.com", "invited@example.com")

        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns emptyMap()

        messageService.onUserInvitation(event)

        verify(exactly = 0) { mockProvider1.sendNotification(any(), any(), any()) }
    }

    @Test
    fun `onAccountDeletion should send notification when enabled`() {
        val user = User(username = "deleteduser", email = "deleted@example.com", password = "hash")
        val event = UserDeletedEvent(this, user, "http://example.com")
        val filledContent = "<html>Account deleted</html>"

        every { mockProvider1.enabled } returns true
        every { mockProvider1.supportedTemplateType } returns TemplateType.MJML
        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns mapOf(
            "provider1" to mockProvider1
        )
        every {
            templateService.fillMessageTemplate(
                MessageTemplates.AccountDeleted,
                TemplateType.MJML,
                mapOf("username" to "deleteduser", "baseUrl" to "http://example.com")
            )
        } returns filledContent

        messageService.onAccountDeletion(event)

        verify {
            mockProvider1.sendNotification(
                "deleted@example.com",
                "[Gameyfin] Your account has been deleted",
                filledContent
            )
        }
    }

    @Test
    fun `onAccountDeletion should not send when disabled`() {
        val user = User(username = "deleteduser", email = "deleted@example.com", password = "hash")
        val event = UserDeletedEvent(this, user, "http://example.com")

        every { applicationContext.getBeansOfType<AbstractMessageProvider>() } returns emptyMap()

        messageService.onAccountDeletion(event)

        verify(exactly = 0) { mockProvider1.sendNotification(any(), any(), any()) }
    }

    private fun setupSecurityContext(username: String) {
        val authentication = mockk<Authentication>()
        val securityContext = mockk<SecurityContext>()
        every { authentication.name } returns username
        every { securityContext.authentication } returns authentication
        SecurityContextHolder.setContext(securityContext)
    }
}

