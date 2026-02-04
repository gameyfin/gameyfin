package org.gameyfin.app.messages.providers

import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.messages.templates.TemplateType
import org.springframework.stereotype.Service
import java.util.*

@Service
class EmailMessageProvider(
    config: ConfigService
) : AbstractMessageProvider("email", TemplateType.MJML, config) {

    private val storedCredentials: Properties
        get() {
            val properties = Properties()
            properties["host"] = config.get(ConfigProperties.Messages.Providers.Email.Host)
            properties["port"] = config.get(ConfigProperties.Messages.Providers.Email.Port)
            properties["username"] = config.get(ConfigProperties.Messages.Providers.Email.Username)
            properties["password"] = config.get(ConfigProperties.Messages.Providers.Email.Password)
            return properties
        }

    override fun testCredentials(credentials: Properties): Boolean {
        try {
            val sessionProperties = Properties()
            sessionProperties["mail.smtp.auth"] = true
            sessionProperties["mail.smtp.starttls.enable"] = true
            sessionProperties["mail.smtp.host"] = credentials["host"] as String
            sessionProperties["mail.smtp.port"] = credentials["port"] as Int

            val session = Session.getInstance(sessionProperties)

            val transport = session.getTransport("smtp")
            transport.connect(
                credentials["host"] as String,
                credentials["port"] as Int,
                credentials["username"] as String,
                credentials["password"] as String
            )
            transport.close()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    override fun sendNotification(recipient: String, title: String, message: String) {
        val credentials = storedCredentials
        val sessionProperties = Properties()
        sessionProperties["mail.smtp.auth"] = true
        sessionProperties["mail.smtp.starttls.enable"] = true
        sessionProperties["mail.smtp.host"] = credentials["host"]
        sessionProperties["mail.smtp.port"] = credentials["port"]

        val session = Session.getInstance(sessionProperties)

        val mimeMessage = MimeMessage(session)
        mimeMessage.setFrom(InternetAddress(credentials["username"] as String))
        mimeMessage.setRecipients(Message.RecipientType.TO, recipient)
        mimeMessage.subject = title
        mimeMessage.setContent(message, "text/html; charset=utf-8")

        val transport = session.getTransport("smtp")

        transport.use { transport ->
            transport.connect(
                credentials["host"] as String,
                credentials["port"] as Int,
                credentials["username"] as String,
                credentials["password"] as String
            )
            transport.sendMessage(mimeMessage, mimeMessage.allRecipients)
        }
    }
}