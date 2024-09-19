package de.grimsi.gameyfin.notifications

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Roles
import de.grimsi.gameyfin.notifications.dto.EmailCredentialsDto
import jakarta.annotation.security.RolesAllowed
import jakarta.mail.MessagingException
import jakarta.mail.Session
import java.util.*

@Endpoint
@RolesAllowed(Roles.Names.SUPERADMIN, Roles.Names.ADMIN)
class NotificationEndpoint {

    fun verifyEmailCredentials(credentials: EmailCredentialsDto): Boolean {
        val properties = Properties()
        properties["mail.smtp.auth"] = true
        properties["mail.smtp.starttls.enable"] = true
        properties["mail.smtp.host"] = credentials.host
        properties["mail.smtp.port"] = credentials.port

        val session = Session.getInstance(properties)

        try {
            val transport = session.getTransport("smtp")
            transport.connect(credentials.host, credentials.port, credentials.username, credentials.password)
            transport.close()
            return true
        } catch (_: MessagingException) {
            return false
        }
    }
}