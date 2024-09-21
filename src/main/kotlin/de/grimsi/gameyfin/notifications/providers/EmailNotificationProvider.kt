package de.grimsi.gameyfin.notifications.providers

import de.grimsi.gameyfin.config.ConfigService
import jakarta.mail.MessagingException
import jakarta.mail.Session
import org.springframework.stereotype.Service
import java.util.*

@Service
class EmailNotificationProvider(
    configService: ConfigService
) : AbstractNotificationProvider("email", configService) {

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
        } catch (_: MessagingException) {
            return false
        }
    }

    override fun sendNotification(recipient: String, title: String, message: String) {
        TODO("Not yet implemented")
    }
}