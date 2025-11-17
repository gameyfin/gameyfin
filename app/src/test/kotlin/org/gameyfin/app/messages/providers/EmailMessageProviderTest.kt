package org.gameyfin.app.messages.providers

import io.mockk.*
import jakarta.mail.MessagingException
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.messages.templates.TemplateType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailMessageProviderTest {

    private lateinit var configService: ConfigService
    private lateinit var emailProvider: EmailMessageProvider

    @BeforeEach
    fun setup() {
        configService = mockk()
        emailProvider = EmailMessageProvider(configService)

        mockkStatic(Session::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `providerKey should be email`() {
        assertEquals("email", emailProvider.providerKey)
    }

    @Test
    fun `supportedTemplateType should be MJML`() {
        assertEquals(TemplateType.MJML, emailProvider.supportedTemplateType)
    }

    @Test
    fun `enabled should return config value`() {
        every { configService.get("messages.providers.email.enabled") } returns true

        val result = emailProvider.enabled

        assertTrue(result)
        verify { configService.get("messages.providers.email.enabled") }
    }

    @Test
    fun `enabled should return false when disabled in config`() {
        every { configService.get("messages.providers.email.enabled") } returns false

        val result = emailProvider.enabled

        assertFalse(result)
    }

    @Test
    fun `testCredentials should return true when connection succeeds`() {
        val credentials = Properties()
        credentials["host"] = "smtp.example.com"
        credentials["port"] = 587
        credentials["username"] = "user@example.com"
        credentials["password"] = "secret"

        val mockSession = mockk<Session>()
        val mockTransport = mockk<Transport>(relaxed = true)

        every { Session.getInstance(any<Properties>()) } returns mockSession
        every { mockSession.getTransport("smtp") } returns mockTransport
        every { mockTransport.connect("smtp.example.com", 587, "user@example.com", "secret") } just Runs
        every { mockTransport.close() } just Runs

        val result = emailProvider.testCredentials(credentials)

        assertTrue(result)
        verify { mockTransport.connect("smtp.example.com", 587, "user@example.com", "secret") }
        verify { mockTransport.close() }
    }

    @Test
    fun `testCredentials should return false when connection fails`() {
        val credentials = Properties()
        credentials["host"] = "smtp.example.com"
        credentials["port"] = 587
        credentials["username"] = "user@example.com"
        credentials["password"] = "wrongpassword"

        val mockSession = mockk<Session>()
        val mockTransport = mockk<Transport>()

        every { Session.getInstance(any<Properties>()) } returns mockSession
        every { mockSession.getTransport("smtp") } returns mockTransport
        every {
            mockTransport.connect("smtp.example.com", 587, "user@example.com", "wrongpassword")
        } throws MessagingException("Authentication failed")

        val result = emailProvider.testCredentials(credentials)

        assertFalse(result)
    }

    @Test
    fun `testCredentials should handle missing credentials gracefully`() {
        val credentials = Properties()
        credentials["host"] = "smtp.example.com"

        val mockSession = mockk<Session>()
        val mockTransport = mockk<Transport>()

        every { Session.getInstance(any<Properties>()) } returns mockSession
        every { mockSession.getTransport("smtp") } returns mockTransport
        every { mockTransport.connect(any(), any(), any(), any()) } throws MessagingException("Invalid credentials")

        val result = emailProvider.testCredentials(credentials)

        assertFalse(result)
    }

    @Test
    fun `testCredentials should configure SMTP properties correctly`() {
        val credentials = Properties()
        credentials["host"] = "smtp.example.com"
        credentials["port"] = 465
        credentials["username"] = "user@example.com"
        credentials["password"] = "secret"

        val mockSession = mockk<Session>()
        val mockTransport = mockk<Transport>(relaxed = true)
        var capturedProperties: Properties? = null

        every { Session.getInstance(any<Properties>()) } answers {
            capturedProperties = firstArg()
            mockSession
        }
        every { mockSession.getTransport("smtp") } returns mockTransport

        emailProvider.testCredentials(credentials)

        assertEquals("true", capturedProperties?.get("mail.smtp.auth")?.toString())
        assertEquals("true", capturedProperties?.get("mail.smtp.starttls.enable")?.toString())
        assertEquals("smtp.example.com", capturedProperties?.get("mail.smtp.host"))
        assertEquals(465, capturedProperties?.get("mail.smtp.port"))
    }

    @Test
    fun `sendNotification should send email with correct parameters`() {
        val recipient = "recipient@example.com"
        val title = "Test Subject"
        val message = "<html><body>Test message</body></html>"

        every { configService.get(ConfigProperties.Messages.Providers.Email.Host) } returns "smtp.example.com"
        every { configService.get(ConfigProperties.Messages.Providers.Email.Port) } returns 587
        every { configService.get(ConfigProperties.Messages.Providers.Email.Username) } returns "sender@example.com"
        every { configService.get(ConfigProperties.Messages.Providers.Email.Password) } returns "password"

        val mockSession = mockk<Session>()
        val mockTransport = mockk<Transport>(relaxed = true)
        val mimeMessageSlot = slot<MimeMessage>()

        every { Session.getInstance(any<Properties>()) } returns mockSession
        every { mockSession.properties } returns Properties()
        every { mockSession.getTransport("smtp") } returns mockTransport
        every { mockTransport.connect(any(), any(), any(), any()) } just Runs
        every { mockTransport.sendMessage(capture(mimeMessageSlot), any()) } just Runs
        every { mockTransport.close() } just Runs

        mockkConstructor(MimeMessage::class)
        every { anyConstructed<MimeMessage>().setFrom(any<InternetAddress>()) } just Runs
        every { anyConstructed<MimeMessage>().setRecipients(any(), any<String>()) } just Runs
        every { anyConstructed<MimeMessage>().subject = any() } just Runs
        every { anyConstructed<MimeMessage>().setContent(any(), any()) } just Runs
        every { anyConstructed<MimeMessage>().allRecipients } returns emptyArray()

        emailProvider.sendNotification(recipient, title, message)

        verify { mockTransport.connect("smtp.example.com", 587, "sender@example.com", "password") }
        verify { mockTransport.sendMessage(any(), any()) }
        verify { mockTransport.close() }
        verify { anyConstructed<MimeMessage>().setRecipients(any(), recipient) }
        verify { anyConstructed<MimeMessage>().subject = title }
        verify { anyConstructed<MimeMessage>().setContent(message, "text/html; charset=utf-8") }
    }

    @Test
    fun `sendNotification should retrieve credentials from config`() {
        val recipient = "recipient@example.com"
        val title = "Test"
        val message = "Test message"

        every { configService.get(ConfigProperties.Messages.Providers.Email.Host) } returns "mail.example.com"
        every { configService.get(ConfigProperties.Messages.Providers.Email.Port) } returns 465
        every { configService.get(ConfigProperties.Messages.Providers.Email.Username) } returns "noreply@example.com"
        every { configService.get(ConfigProperties.Messages.Providers.Email.Password) } returns "securepass"

        val mockSession = mockk<Session>()
        val mockTransport = mockk<Transport>(relaxed = true)

        every { Session.getInstance(any<Properties>()) } returns mockSession
        every { mockSession.getTransport("smtp") } returns mockTransport
        every { mockSession.properties } returns Properties()

        mockkConstructor(MimeMessage::class)
        every { anyConstructed<MimeMessage>().setFrom(any<InternetAddress>()) } just Runs
        every { anyConstructed<MimeMessage>().setRecipients(any(), any<String>()) } just Runs
        every { anyConstructed<MimeMessage>().subject = any() } just Runs
        every { anyConstructed<MimeMessage>().setContent(any(), any()) } just Runs
        every { anyConstructed<MimeMessage>().allRecipients } returns emptyArray()

        emailProvider.sendNotification(recipient, title, message)

        verify { configService.get(ConfigProperties.Messages.Providers.Email.Host) }
        verify { configService.get(ConfigProperties.Messages.Providers.Email.Port) }
        verify { configService.get(ConfigProperties.Messages.Providers.Email.Username) }
        verify { configService.get(ConfigProperties.Messages.Providers.Email.Password) }
        verify { mockTransport.connect("mail.example.com", 465, "noreply@example.com", "securepass") }
    }

    @Test
    fun `sendNotification should configure SMTP session properties correctly`() {
        val recipient = "recipient@example.com"
        val title = "Test"
        val message = "Test message"

        every { configService.get(ConfigProperties.Messages.Providers.Email.Host) } returns "smtp.example.com"
        every { configService.get(ConfigProperties.Messages.Providers.Email.Port) } returns 587
        every { configService.get(ConfigProperties.Messages.Providers.Email.Username) } returns "sender@example.com"
        every { configService.get(ConfigProperties.Messages.Providers.Email.Password) } returns "password"

        val mockSession = mockk<Session>()
        val mockTransport = mockk<Transport>(relaxed = true)
        var capturedProperties: Properties? = null

        every { Session.getInstance(any<Properties>()) } answers {
            capturedProperties = firstArg()
            mockSession
        }
        every { mockSession.getTransport("smtp") } returns mockTransport
        every { mockSession.properties } returns Properties()

        mockkConstructor(MimeMessage::class)
        every { anyConstructed<MimeMessage>().setFrom(any<InternetAddress>()) } just Runs
        every { anyConstructed<MimeMessage>().setRecipients(any(), any<String>()) } just Runs
        every { anyConstructed<MimeMessage>().subject = any() } just Runs
        every { anyConstructed<MimeMessage>().setContent(any(), any()) } just Runs
        every { anyConstructed<MimeMessage>().allRecipients } returns emptyArray()

        emailProvider.sendNotification(recipient, title, message)

        assertEquals("true", capturedProperties?.get("mail.smtp.auth")?.toString())
        assertEquals("true", capturedProperties?.get("mail.smtp.starttls.enable")?.toString())
        assertEquals("smtp.example.com", capturedProperties?.get("mail.smtp.host"))
        assertEquals(587, capturedProperties?.get("mail.smtp.port"))
    }

    @Test
    fun `sendNotification should propagate MessagingException when send fails`() {
        val recipient = "recipient@example.com"
        val title = "Test"
        val message = "Test message"

        every { configService.get(ConfigProperties.Messages.Providers.Email.Host) } returns "smtp.example.com"
        every { configService.get(ConfigProperties.Messages.Providers.Email.Port) } returns 587
        every { configService.get(ConfigProperties.Messages.Providers.Email.Username) } returns "sender@example.com"
        every { configService.get(ConfigProperties.Messages.Providers.Email.Password) } returns "password"

        val mockSession = mockk<Session>()
        val mockTransport = mockk<Transport>()

        every { Session.getInstance(any<Properties>()) } returns mockSession
        every { mockSession.getTransport("smtp") } returns mockTransport
        every { mockSession.properties } returns Properties()
        every { mockTransport.connect(any(), any(), any(), any()) } just Runs
        every { mockTransport.close() } just Runs
        every { mockTransport.sendMessage(any(), any()) } throws MessagingException("Send failed")

        mockkConstructor(MimeMessage::class)
        every { anyConstructed<MimeMessage>().setFrom(any<InternetAddress>()) } just Runs
        every { anyConstructed<MimeMessage>().setRecipients(any(), any<String>()) } just Runs
        every { anyConstructed<MimeMessage>().subject = any() } just Runs
        every { anyConstructed<MimeMessage>().setContent(any(), any()) } just Runs
        every { anyConstructed<MimeMessage>().allRecipients } returns emptyArray()

        assertThrows(MessagingException::class.java) {
            emailProvider.sendNotification(recipient, title, message)
        }
    }

    @Test
    fun `sendNotification should close transport even when exception occurs`() {
        val recipient = "recipient@example.com"
        val title = "Test"
        val message = "Test message"

        every { configService.get(ConfigProperties.Messages.Providers.Email.Host) } returns "smtp.example.com"
        every { configService.get(ConfigProperties.Messages.Providers.Email.Port) } returns 587
        every { configService.get(ConfigProperties.Messages.Providers.Email.Username) } returns "sender@example.com"
        every { configService.get(ConfigProperties.Messages.Providers.Email.Password) } returns "password"

        val mockSession = mockk<Session>()
        val mockTransport = mockk<Transport>(relaxed = true)

        every { Session.getInstance(any<Properties>()) } returns mockSession
        every { mockSession.getTransport("smtp") } returns mockTransport
        every { mockSession.properties } returns Properties()
        every { mockTransport.sendMessage(any(), any()) } throws MessagingException("Send failed")

        mockkConstructor(MimeMessage::class)
        every { anyConstructed<MimeMessage>().setFrom(any<InternetAddress>()) } just Runs
        every { anyConstructed<MimeMessage>().setRecipients(any(), any<String>()) } just Runs
        every { anyConstructed<MimeMessage>().subject = any() } just Runs
        every { anyConstructed<MimeMessage>().setContent(any(), any()) } just Runs
        every { anyConstructed<MimeMessage>().allRecipients } returns emptyArray()

        try {
            emailProvider.sendNotification(recipient, title, message)
        } catch (_: MessagingException) {
        }

        verify { mockTransport.close() }
    }

    @Test
    fun `sendNotification should handle special characters in message content`() {
        val recipient = "recipient@example.com"
        val title = "Test with ÄÖÜ äöü ß"
        val message = "<html><body>Special chars: ÄÖÜ äöü ß € @</body></html>"

        every { configService.get(ConfigProperties.Messages.Providers.Email.Host) } returns "smtp.example.com"
        every { configService.get(ConfigProperties.Messages.Providers.Email.Port) } returns 587
        every { configService.get(ConfigProperties.Messages.Providers.Email.Username) } returns "sender@example.com"
        every { configService.get(ConfigProperties.Messages.Providers.Email.Password) } returns "password"

        val mockSession = mockk<Session>()
        val mockTransport = mockk<Transport>(relaxed = true)

        every { Session.getInstance(any<Properties>()) } returns mockSession
        every { mockSession.properties } returns Properties()
        every { mockSession.getTransport("smtp") } returns mockTransport

        mockkConstructor(MimeMessage::class)
        every { anyConstructed<MimeMessage>().setFrom(any<InternetAddress>()) } just Runs
        every { anyConstructed<MimeMessage>().setRecipients(any(), any<String>()) } just Runs
        every { anyConstructed<MimeMessage>().subject = any() } just Runs
        every { anyConstructed<MimeMessage>().setContent(any(), any()) } just Runs
        every { anyConstructed<MimeMessage>().allRecipients } returns emptyArray()

        emailProvider.sendNotification(recipient, title, message)

        verify { anyConstructed<MimeMessage>().subject = title }
        verify { anyConstructed<MimeMessage>().setContent(message, "text/html; charset=utf-8") }
    }
}

