package de.grimsi.gameyfin.notifications.templates

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Service
class MessageTemplateService {

    companion object {
        private const val TEMPLATE_PATH = "templates"
        private const val TEMPLATE_EXTENSION = "html"
    }

    private val log = KotlinLogging.logger {}

    fun getMessageTemplates(): List<MessageTemplateDto> {
        log.info { "Getting all message templates" }
        val messageTemplates = MessageTemplates::class.sealedSubclasses.flatMap { subclass ->
            subclass.objectInstance?.let { listOf(it) } ?: listOf()
        }
        return messageTemplates.map { MessageTemplateDto(it.key, it.name, it.description, it.availablePlaceholders) }
    }

    fun getMessageTemplate(key: String): MessageTemplates {
        return MessageTemplates::class.sealedSubclasses
            .mapNotNull { it.objectInstance }
            .find { it.key == key }
            ?: throw IllegalArgumentException("Message template with key '$key' not found")
    }

    fun getMessageTemplateContent(key: String): String {
        log.info { "Reading message template content for key '$key'" }
        return getOrCreateTemplateFile(key).readText()
    }

    fun fillMessageTemplate(template: MessageTemplates, placeholders: Map<String, String>): String {
        if (placeholders.keys != template.availablePlaceholders.toSet()) {
            throw IllegalArgumentException("Placeholders do not match available placeholders for template '${template.key}'")
        }

        val content = getMessageTemplateContent(template.key)
        return placeholders.entries.fold(content) { acc, (placeholder, value) ->
            acc.replace("{$placeholder}", value)
        }
    }

    fun setMessageTemplateContent(key: String, content: String) {
        log.info { "Saving message template content for key '$key'" }
        getOrCreateTemplateFile(key).writeText(content)
    }

    private fun getOrCreateTemplateFile(key: String): Path {
        val path = Path.of("./", TEMPLATE_PATH, "$key.$TEMPLATE_EXTENSION")
        if (Files.notExists(path)) {
            Files.createDirectories(path.parent)
            Files.createFile(path)
        }
        return path
    }
}