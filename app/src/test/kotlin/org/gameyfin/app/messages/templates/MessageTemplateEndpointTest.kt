package org.gameyfin.app.messages.templates

import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MessageTemplateEndpointTest {

    private lateinit var messageTemplateService: MessageTemplateService
    private lateinit var messageTemplateEndpoint: MessageTemplateEndpoint

    @BeforeEach
    fun setup() {
        messageTemplateService = mockk()
        messageTemplateEndpoint = MessageTemplateEndpoint(messageTemplateService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getAll should return list of all message template DTOs`() {
        val expectedTemplates = listOf(
            MessageTemplateDto("user-invitation", "User Invitation", "Description 1", listOf("invitationLink")),
            MessageTemplateDto(
                "password-reset-request",
                "Password Reset",
                "Description 2",
                listOf("username", "resetLink")
            )
        )

        every { messageTemplateService.getMessageTemplates() } returns expectedTemplates

        val result = messageTemplateEndpoint.getAll()

        assertEquals(expectedTemplates, result)
        verify(exactly = 1) { messageTemplateService.getMessageTemplates() }
    }

    @Test
    fun `getAll should return empty list when no templates exist`() {
        every { messageTemplateService.getMessageTemplates() } returns emptyList()

        val result = messageTemplateEndpoint.getAll()

        assertEquals(emptyList(), result)
        verify(exactly = 1) { messageTemplateService.getMessageTemplates() }
    }

    @Test
    fun `get should return specific message template`() {
        val key = "password-reset-request"
        val expectedTemplate = MessageTemplates.PasswordResetRequest

        every { messageTemplateService.getMessageTemplate(key) } returns expectedTemplate

        val result = messageTemplateEndpoint.get(key)

        assertEquals(expectedTemplate, result)
        verify(exactly = 1) { messageTemplateService.getMessageTemplate(key) }
    }

    @Test
    fun `get should delegate to service for different template keys`() {
        val keys = listOf("user-invitation", "waiting-for-approval", "account-enabled")
        val templates = listOf(
            MessageTemplates.UserInvitation,
            MessageTemplates.WaitingForApproval,
            MessageTemplates.AccountEnabled
        )

        keys.forEachIndexed { index, key ->
            every { messageTemplateService.getMessageTemplate(key) } returns templates[index]

            val result = messageTemplateEndpoint.get(key)

            assertEquals(templates[index], result)
        }

        verify(exactly = 3) { messageTemplateService.getMessageTemplate(any()) }
    }

    @Test
    fun `getDefaultPlaceholders should return list of placeholder keys for MJML`() {
        val expectedPlaceholders = mapOf("logo" to "data:image...", "gradient" to "data:image...")

        every { messageTemplateService.getDefaultTemplatePlaceholders(TemplateType.MJML) } returns expectedPlaceholders

        val result = messageTemplateEndpoint.getDefaultPlaceholders(TemplateType.MJML)

        assertEquals(listOf("logo", "gradient"), result)
        verify(exactly = 1) { messageTemplateService.getDefaultTemplatePlaceholders(TemplateType.MJML) }
    }

    @Test
    fun `getDefaultPlaceholders should return empty list for TEXT template type`() {
        every { messageTemplateService.getDefaultTemplatePlaceholders(TemplateType.TEXT) } returns emptyMap()

        val result = messageTemplateEndpoint.getDefaultPlaceholders(TemplateType.TEXT)

        assertEquals(emptyList(), result)
        verify(exactly = 1) { messageTemplateService.getDefaultTemplatePlaceholders(TemplateType.TEXT) }
    }

    @Test
    fun `getDefaultPlaceholders should handle multiple placeholders`() {
        val placeholders = mapOf(
            "logo" to "data:logo",
            "gradient" to "data:gradient",
            "footer" to "data:footer"
        )

        every { messageTemplateService.getDefaultTemplatePlaceholders(TemplateType.MJML) } returns placeholders

        val result = messageTemplateEndpoint.getDefaultPlaceholders(TemplateType.MJML)

        assertEquals(3, result.size)
        assertEquals(placeholders.keys.toList(), result)
    }

    @Test
    fun `read should return template content as string`() {
        val key = "password-reset-request"
        val templateType = TemplateType.MJML
        val expectedContent = "<mjml>Template content</mjml>"

        every { messageTemplateService.getMessageTemplateContent(key, templateType) } returns expectedContent

        val result = messageTemplateEndpoint.read(key, templateType)

        assertEquals(expectedContent, result)
        verify(exactly = 1) { messageTemplateService.getMessageTemplateContent(key, templateType) }
    }

    @Test
    fun `read should handle TEXT template type`() {
        val key = "password-reset-request"
        val templateType = TemplateType.TEXT
        val expectedContent = "Plain text template content"

        every { messageTemplateService.getMessageTemplateContent(key, templateType) } returns expectedContent

        val result = messageTemplateEndpoint.read(key, templateType)

        assertEquals(expectedContent, result)
        verify(exactly = 1) { messageTemplateService.getMessageTemplateContent(key, templateType) }
    }

    @Test
    fun `read should handle different template keys`() {
        val keys = listOf("user-invitation", "account-enabled", "account-deleted")
        val contents = listOf("Content 1", "Content 2", "Content 3")

        keys.forEachIndexed { index, key ->
            every { messageTemplateService.getMessageTemplateContent(key, TemplateType.MJML) } returns contents[index]

            val result = messageTemplateEndpoint.read(key, TemplateType.MJML)

            assertEquals(contents[index], result)
        }

        verify(exactly = 3) { messageTemplateService.getMessageTemplateContent(any(), TemplateType.MJML) }
    }

    @Test
    fun `save should delegate to service with correct parameters`() {
        val key = "password-reset-request"
        val templateType = TemplateType.MJML
        val content = "<mjml>New template content</mjml>"

        every { messageTemplateService.setMessageTemplateContent(key, templateType, content) } just Runs

        messageTemplateEndpoint.save(key, templateType, content)

        verify(exactly = 1) { messageTemplateService.setMessageTemplateContent(key, templateType, content) }
    }

    @Test
    fun `save should handle TEXT template type`() {
        val key = "waiting-for-approval"
        val templateType = TemplateType.TEXT
        val content = "Plain text content"

        every { messageTemplateService.setMessageTemplateContent(key, templateType, content) } just Runs

        messageTemplateEndpoint.save(key, templateType, content)

        verify(exactly = 1) { messageTemplateService.setMessageTemplateContent(key, templateType, content) }
    }

    @Test
    fun `save should handle empty content`() {
        val key = "user-invitation"
        val templateType = TemplateType.MJML
        val content = ""

        every { messageTemplateService.setMessageTemplateContent(key, templateType, content) } just Runs

        messageTemplateEndpoint.save(key, templateType, content)

        verify(exactly = 1) { messageTemplateService.setMessageTemplateContent(key, templateType, content) }
    }

    @Test
    fun `save should handle large content`() {
        val key = "email-confirmation"
        val templateType = TemplateType.MJML
        val content = "<mjml>" + "x".repeat(10000) + "</mjml>"

        every { messageTemplateService.setMessageTemplateContent(key, templateType, content) } just Runs

        messageTemplateEndpoint.save(key, templateType, content)

        verify(exactly = 1) { messageTemplateService.setMessageTemplateContent(key, templateType, content) }
    }

    @Test
    fun `save should handle content with special characters`() {
        val key = "account-disabled"
        val templateType = TemplateType.MJML
        val content = "<mjml>Content with special chars: äöü ÄÖÜ ß € @ # $ % & *</mjml>"

        every { messageTemplateService.setMessageTemplateContent(key, templateType, content) } just Runs

        messageTemplateEndpoint.save(key, templateType, content)

        verify(exactly = 1) { messageTemplateService.setMessageTemplateContent(key, templateType, content) }
    }
}

