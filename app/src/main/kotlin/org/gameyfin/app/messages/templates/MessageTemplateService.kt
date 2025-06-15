package org.gameyfin.app.messages.templates

import ch.digitalfondue.mjml4j.Mjml4j
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Service
class MessageTemplateService {

    companion object {
        private const val TEMPLATE_PATH = "templates"
        private const val DEFAULT_TEMPLATE_PATH = "templates/messages"
    }

    private val log = KotlinLogging.logger {}

    fun getMessageTemplates(): List<MessageTemplateDto> {
        log.debug { "Getting all message templates" }
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

    fun getMessageTemplateContent(key: String, type: TemplateType): String {
        log.debug { "Reading message template content for '$key.${type.extension}'" }
        return getTemplateFile(key, type).readText()
    }

    fun fillMessageTemplate(template: MessageTemplates, type: TemplateType, placeholders: Map<String, String>): String {
        if (placeholders.keys != template.availablePlaceholders.toSet()) {
            throw IllegalArgumentException("Placeholders do not match available placeholders for template '${template.key}'")
        }

        val content = getMessageTemplateContent(template.key, type)

        return when (type) {
            TemplateType.TEXT -> fillTextTemplate(content, placeholders)
            TemplateType.MJML -> fillMjmlTemplate(content, placeholders)
        }
    }

    fun setMessageTemplateContent(key: String, type: TemplateType, content: String) {
        log.debug { "Saving message template content for key '$key'" }
        getOrCreateTemplateFile(key, type).writeText(content)
    }

    fun getDefaultTemplatePlaceholders(templateType: TemplateType): Map<String, String> {
        return when (templateType) {
            TemplateType.TEXT -> emptyMap()
            TemplateType.MJML -> MjmlTemplate.placeholders
        }
    }

    private fun getTemplateFile(key: String, type: TemplateType): Path {
        val path = Path.of("./", TEMPLATE_PATH, "$key.${type.extension}")
        if (Files.notExists(path)) return getDefaultTemplateFile(key, type)
        return path
    }

    private fun getOrCreateTemplateFile(key: String, type: TemplateType): Path {
        val path = Path.of("./", TEMPLATE_PATH, "$key.${type.extension}")
        if (Files.notExists(path)) {
            Files.createDirectories(path.parent)
            Files.createFile(path)
        }
        return path
    }

    private fun getDefaultTemplateFile(key: String, type: TemplateType): Path {
        log.debug { "No custom message template found for '$key.${type.extension}', returning default" }
        val resourceUrl = javaClass.classLoader.getResource("$DEFAULT_TEMPLATE_PATH/$key.${type.extension}")
            ?: throw IllegalStateException("Default template file not found for '$key.${type.extension}'")
        return Paths.get(resourceUrl.toURI())
    }

    private fun fillTextTemplate(content: String, placeholders: Map<String, String>): String {
        return placeholders.entries.fold(content) { acc, (placeholder, value) ->
            acc.replace("{$placeholder}", value)
        }
    }

    private fun fillMjmlTemplate(content: String, placeholders: Map<String, String>): String {
        val withDefaultPlaceholders = placeholders + getDefaultTemplatePlaceholders(TemplateType.MJML)
        val contentWithFilledPlaceholders = fillTextTemplate(content, withDefaultPlaceholders)
        return Mjml4j.render(contentWithFilledPlaceholders)
    }
}