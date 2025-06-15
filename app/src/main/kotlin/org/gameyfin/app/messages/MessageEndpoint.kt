package org.gameyfin.app.messages

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class MessageEndpoint(
    private val messageService: MessageService
) {

    @AnonymousAllowed
    fun isEnabled(): Boolean {
        return messageService.enabled
    }

    fun verifyCredentials(provider: String, credentials: Map<String, Any>): Boolean {
        return messageService.testCredentials(provider, credentials)
    }

    fun sendTestNotification(templateKey: String, placeholders: Map<String, String>): Boolean {
        return messageService.sendTestNotification(templateKey, placeholders)
    }
}