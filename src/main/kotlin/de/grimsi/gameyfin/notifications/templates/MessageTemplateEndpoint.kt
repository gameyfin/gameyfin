package de.grimsi.gameyfin.notifications.templates

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Roles
import jakarta.annotation.security.RolesAllowed

@RolesAllowed(Roles.Names.SUPERADMIN, Roles.Names.ADMIN)
@Endpoint
class MessageTemplateEndpoint(
    private val messageTemplateService: MessageTemplateService
) {
    fun getAll(): List<MessageTemplateDto> {
        return messageTemplateService.getMessageTemplates()
    }

    fun get(key: String): MessageTemplates {
        return messageTemplateService.getMessageTemplate(key)
    }

    fun read(key: String): String {
        return messageTemplateService.getMessageTemplateContent(key)
    }

    fun save(key: String, content: String) {
        messageTemplateService.setMessageTemplateContent(key, content)
    }
}