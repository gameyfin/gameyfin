package org.gameyfin.app.messages.templates

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MessageTemplateServiceTest {

    private lateinit var messageTemplateService: MessageTemplateService

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        messageTemplateService = MessageTemplateService()

        mockkStatic(Path::class)
        every { Path.of(any(), any(), any()) } answers {
            val fileName = arg<Array<String>>(1)[1]
            tempDir.resolve(fileName)
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getMessageTemplates should return all message templates as DTOs`() {
        val result = messageTemplateService.getMessageTemplates()

        assertTrue(result.isNotEmpty())
        assertTrue(result.any { it.key == "user-invitation" })
        assertTrue(result.any { it.key == "password-reset-request" })
        assertTrue(result.any { it.key == "waiting-for-approval" })
        assertTrue(result.any { it.key == "account-enabled" })
        assertTrue(result.any { it.key == "account-disabled" })
        assertTrue(result.any { it.key == "account-deleted" })
        assertTrue(result.any { it.key == "email-already-registered" })
        assertTrue(result.any { it.key == "email-confirmation" })
    }

    @Test
    fun `getMessageTemplates should return DTOs with correct properties`() {
        val result = messageTemplateService.getMessageTemplates()

        val passwordResetTemplate = result.find { it.key == "password-reset-request" }
        assertNotNull(passwordResetTemplate)
        assertEquals("Password Reset Request", passwordResetTemplate.name)
        assertEquals("Template for the password reset request message", passwordResetTemplate.description)
        assertEquals(listOf("username", "resetLink"), passwordResetTemplate.availablePlaceholders)
    }

    @Test
    fun `getMessageTemplates should include all placeholders for each template`() {
        val result = messageTemplateService.getMessageTemplates()

        val userInvitation = result.find { it.key == "user-invitation" }
        assertEquals(listOf("invitationLink"), userInvitation?.availablePlaceholders)

        val accountEnabled = result.find { it.key == "account-enabled" }
        assertEquals(listOf("username", "baseUrl"), accountEnabled?.availablePlaceholders)

        val emailAlreadyRegistered = result.find { it.key == "email-already-registered" }
        assertEquals(listOf("username", "passwordResetLink"), emailAlreadyRegistered?.availablePlaceholders)
    }

    @Test
    fun `getMessageTemplate should return correct template for valid key`() {
        val result = messageTemplateService.getMessageTemplate("password-reset-request")

        assertEquals(MessageTemplates.PasswordResetRequest, result)
        assertEquals("password-reset-request", result.key)
        assertEquals("Password Reset Request", result.name)
    }

    @Test
    fun `getMessageTemplate should return all different templates`() {
        val templates = listOf(
            "user-invitation" to MessageTemplates.UserInvitation,
            "waiting-for-approval" to MessageTemplates.WaitingForApproval,
            "account-enabled" to MessageTemplates.AccountEnabled,
            "account-disabled" to MessageTemplates.AccountDisabled,
            "account-deleted" to MessageTemplates.AccountDeleted,
            "email-already-registered" to MessageTemplates.RegistrationAttemptWithExistingEmail,
            "email-confirmation" to MessageTemplates.EmailConfirmation,
            "password-reset-request" to MessageTemplates.PasswordResetRequest
        )

        templates.forEach { (key, expected) ->
            val result = messageTemplateService.getMessageTemplate(key)
            assertEquals(expected, result)
        }
    }

    @Test
    fun `getMessageTemplate should throw exception for invalid key`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            messageTemplateService.getMessageTemplate("invalid-key")
        }

        assertEquals("Message template with key 'invalid-key' not found", exception.message)
    }

    @Test
    fun `getMessageTemplate should throw exception for empty key`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            messageTemplateService.getMessageTemplate("")
        }

        assertEquals("Message template with key '' not found", exception.message)
    }

    @Test
    fun `getMessageTemplateContent should read custom template when file exists`() {
        val key = "password-reset-request"
        val templateType = TemplateType.MJML
        val expectedContent = "<mjml>Custom template content</mjml>"

        val templateFile = tempDir.resolve("$key.${templateType.extension}")
        templateFile.writeText(expectedContent)

        val result = messageTemplateService.getMessageTemplateContent(key, templateType)

        assertEquals(expectedContent, result)
    }

    @Test
    fun `getMessageTemplateContent should read TEXT template when file exists`() {
        val key = "waiting-for-approval"
        val templateType = TemplateType.TEXT
        val expectedContent = "Plain text template content"

        val templateFile = tempDir.resolve("$key.${templateType.extension}")
        templateFile.writeText(expectedContent)

        val result = messageTemplateService.getMessageTemplateContent(key, templateType)

        assertEquals(expectedContent, result)
    }

    @Test
    fun `getMessageTemplateContent should fall back to default template when custom file does not exist`() {
        val key = "password-reset-request"
        val templateType = TemplateType.MJML

        unmockkStatic(Path::class)

        val result = messageTemplateService.getMessageTemplateContent(key, templateType)

        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `setMessageTemplateContent should create file with content`() {
        val key = "user-invitation"
        val templateType = TemplateType.MJML
        val content = "<mjml>New custom template</mjml>"

        messageTemplateService.setMessageTemplateContent(key, templateType, content)

        val templateFile = tempDir.resolve("$key.${templateType.extension}")
        assertTrue(Files.exists(templateFile))
        assertEquals(content, templateFile.toFile().readText())
    }

    @Test
    fun `setMessageTemplateContent should overwrite existing file`() {
        val key = "account-enabled"
        val templateType = TemplateType.TEXT
        val initialContent = "Initial content"
        val updatedContent = "Updated content"

        val templateFile = tempDir.resolve("$key.${templateType.extension}")
        templateFile.writeText(initialContent)

        messageTemplateService.setMessageTemplateContent(key, templateType, updatedContent)

        assertEquals(updatedContent, templateFile.toFile().readText())
    }

    @Test
    fun `setMessageTemplateContent should handle empty content`() {
        val key = "account-disabled"
        val templateType = TemplateType.MJML
        val content = ""

        messageTemplateService.setMessageTemplateContent(key, templateType, content)

        val templateFile = tempDir.resolve("$key.${templateType.extension}")
        assertTrue(Files.exists(templateFile))
        assertEquals(content, templateFile.toFile().readText())
    }

    @Test
    fun `setMessageTemplateContent should handle special characters`() {
        val key = "email-confirmation"
        val templateType = TemplateType.MJML
        val content = "<mjml>Special chars: äöü ÄÖÜ ß € @ # $ % & *</mjml>"

        messageTemplateService.setMessageTemplateContent(key, templateType, content)

        val templateFile = tempDir.resolve("$key.${templateType.extension}")
        assertEquals(content, templateFile.toFile().readText())
    }

    @Test
    fun `fillMessageTemplate should replace placeholders in TEXT template`() {
        val template = MessageTemplates.WaitingForApproval
        val placeholders = mapOf("username" to "testuser")
        val templateContent = "Hello {username}, your account is waiting for approval."

        val templateFile = tempDir.resolve("${template.key}.txt")
        templateFile.writeText(templateContent)

        val result = messageTemplateService.fillMessageTemplate(template, TemplateType.TEXT, placeholders)

        assertEquals("Hello testuser, your account is waiting for approval.", result)
    }

    @Test
    fun `fillMessageTemplate should replace multiple placeholders`() {
        val template = MessageTemplates.AccountEnabled
        val placeholders = mapOf("username" to "john", "baseUrl" to "http://example.com")
        val templateContent = "Hello {username}, your account at {baseUrl} has been enabled."

        val templateFile = tempDir.resolve("${template.key}.txt")
        templateFile.writeText(templateContent)

        val result = messageTemplateService.fillMessageTemplate(template, TemplateType.TEXT, placeholders)

        assertEquals("Hello john, your account at http://example.com has been enabled.", result)
    }

    @Test
    fun `fillMessageTemplate should throw exception when placeholders do not match`() {
        val template = MessageTemplates.PasswordResetRequest
        val placeholders = mapOf("username" to "testuser")
        val templateContent = "Hello {username}"

        val templateFile = tempDir.resolve("${template.key}.txt")
        templateFile.writeText(templateContent)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            messageTemplateService.fillMessageTemplate(template, TemplateType.TEXT, placeholders)
        }

        assertTrue(exception.message!!.contains("Placeholders do not match"))
    }

    @Test
    fun `fillMessageTemplate should throw exception when extra placeholders provided`() {
        val template = MessageTemplates.WaitingForApproval
        val placeholders = mapOf("username" to "testuser", "extra" to "value")
        val templateContent = "Hello {username}"

        val templateFile = tempDir.resolve("${template.key}.txt")
        templateFile.writeText(templateContent)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            messageTemplateService.fillMessageTemplate(template, TemplateType.TEXT, placeholders)
        }

        assertTrue(exception.message!!.contains("Placeholders do not match"))
    }

    @Test
    fun `fillMessageTemplate should throw exception when missing required placeholders`() {
        val template = MessageTemplates.PasswordResetRequest
        val placeholders = mapOf("username" to "testuser")
        val templateContent = "Hello {username}"

        val templateFile = tempDir.resolve("${template.key}.txt")
        templateFile.writeText(templateContent)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            messageTemplateService.fillMessageTemplate(template, TemplateType.TEXT, placeholders)
        }

        assertTrue(exception.message!!.contains("Placeholders do not match"))
    }

    @Test
    fun `fillMessageTemplate should handle empty placeholder values`() {
        val template = MessageTemplates.WaitingForApproval
        val placeholders = mapOf("username" to "")
        val templateContent = "Hello {username}!"

        val templateFile = tempDir.resolve("${template.key}.txt")
        templateFile.writeText(templateContent)

        val result = messageTemplateService.fillMessageTemplate(template, TemplateType.TEXT, placeholders)

        assertEquals("Hello !", result)
    }

    @Test
    fun `fillMessageTemplate should handle special characters in placeholder values`() {
        val template = MessageTemplates.WaitingForApproval
        val placeholders = mapOf("username" to "test@user.com")
        val templateContent = "Hello {username}!"

        val templateFile = tempDir.resolve("${template.key}.txt")
        templateFile.writeText(templateContent)

        val result = messageTemplateService.fillMessageTemplate(template, TemplateType.TEXT, placeholders)

        assertEquals("Hello test@user.com!", result)
    }

    @Test
    fun `fillMessageTemplate should handle same placeholder multiple times`() {
        val template = MessageTemplates.WaitingForApproval
        val placeholders = mapOf("username" to "john")
        val templateContent = "Hello {username}, welcome {username}!"

        val templateFile = tempDir.resolve("${template.key}.txt")
        templateFile.writeText(templateContent)

        val result = messageTemplateService.fillMessageTemplate(template, TemplateType.TEXT, placeholders)

        assertEquals("Hello john, welcome john!", result)
    }

    @Test
    fun `fillMessageTemplate should process MJML template and convert to HTML`() {
        val template = MessageTemplates.WaitingForApproval
        val placeholders = mapOf("username" to "testuser")
        val mjmlContent = """
            <mjml>
                <mj-body>
                    <mj-section>
                        <mj-column>
                            <mj-text>Hello {username}</mj-text>
                        </mj-column>
                    </mj-section>
                </mj-body>
            </mjml>
        """.trimIndent()

        val templateFile = tempDir.resolve("${template.key}.mjml")
        templateFile.writeText(mjmlContent)

        val result = messageTemplateService.fillMessageTemplate(template, TemplateType.MJML, placeholders)

        assertTrue(result.contains("<!doctype html>", ignoreCase = true))
        assertTrue(result.contains("Hello testuser"))
    }

    @Test
    fun `fillMessageTemplate should add default MJML placeholders to MJML template`() {
        val template = MessageTemplates.WaitingForApproval
        val placeholders = mapOf("username" to "testuser")
        val mjmlContent = """
            <mjml>
                <mj-body>
                    <mj-section>
                        <mj-column>
                            <mj-image src="{logo}" />
                            <mj-text>Hello {username}</mj-text>
                        </mj-column>
                    </mj-section>
                </mj-body>
            </mjml>
        """.trimIndent()

        val templateFile = tempDir.resolve("${template.key}.mjml")
        templateFile.writeText(mjmlContent)

        val result = messageTemplateService.fillMessageTemplate(template, TemplateType.MJML, placeholders)

        assertTrue(result.contains("Hello testuser"))
        assertTrue(result.contains("data:image/png;base64"))
    }

    @Test
    fun `getDefaultTemplatePlaceholders should return MJML placeholders for MJML type`() {
        val result = messageTemplateService.getDefaultTemplatePlaceholders(TemplateType.MJML)

        assertTrue(result.containsKey("logo"))
        assertTrue(result.containsKey("gradient"))
        assertTrue(result["logo"]!!.startsWith("data:image/png;base64"))
        assertTrue(result["gradient"]!!.startsWith("data:image/png;base64"))
    }

    @Test
    fun `getDefaultTemplatePlaceholders should return empty map for TEXT type`() {
        val result = messageTemplateService.getDefaultTemplatePlaceholders(TemplateType.TEXT)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getDefaultTemplatePlaceholders should return correct number of placeholders`() {
        val result = messageTemplateService.getDefaultTemplatePlaceholders(TemplateType.MJML)

        assertEquals(2, result.size)
    }

    @Test
    fun `fillMessageTemplate should handle complex MJML with multiple placeholders and defaults`() {
        val template = MessageTemplates.PasswordResetRequest
        val placeholders = mapOf("username" to "john", "resetLink" to "http://example.com/reset?token=abc123")
        val mjmlContent = """
            <mjml>
                <mj-body>
                    <mj-section>
                        <mj-column>
                            <mj-image src="{logo}" />
                            <mj-text>Hello {username}</mj-text>
                            <mj-button href="{resetLink}">Reset Password</mj-button>
                            <mj-divider border-color="{gradient}" />
                        </mj-column>
                    </mj-section>
                </mj-body>
            </mjml>
        """.trimIndent()

        val templateFile = tempDir.resolve("${template.key}.mjml")
        templateFile.writeText(mjmlContent)

        val result = messageTemplateService.fillMessageTemplate(template, TemplateType.MJML, placeholders)

        assertTrue(result.contains("Hello john"))
        assertTrue(result.contains("http://example.com/reset?token=abc123"))
        assertTrue(result.contains("data:image/png;base64"))
    }

    @Test
    fun `fillMessageTemplate should not include default placeholders in TEXT template`() {
        val template = MessageTemplates.WaitingForApproval
        val placeholders = mapOf("username" to "testuser")
        val templateContent = "Hello {username}, {logo} should not be replaced."

        val templateFile = tempDir.resolve("${template.key}.txt")
        templateFile.writeText(templateContent)

        val result = messageTemplateService.fillMessageTemplate(template, TemplateType.TEXT, placeholders)

        assertEquals("Hello testuser, {logo} should not be replaced.", result)
    }
}

