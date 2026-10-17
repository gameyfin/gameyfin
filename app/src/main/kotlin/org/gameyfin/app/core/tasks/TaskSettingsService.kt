package org.gameyfin.app.core.tasks

import org.gameyfin.app.core.plugins.dto.PluginConfigMetadataDto
import org.gameyfin.app.core.tasks.persistence.TaskSettingEntry
import org.gameyfin.app.core.tasks.persistence.TaskSettingKey
import org.gameyfin.app.core.tasks.persistence.TaskSettingRepository
import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.Serializable

/**
 * Stores the settings of tasks, as declared by [TaskDefinition.settings].
 */
@Service
class TaskSettingsService(private val settingRepository: TaskSettingRepository) {

    companion object {
        fun toDto(meta: ConfigMetadata<*>) = PluginConfigMetadataDto(
            key = meta.key,
            // The UI expects primitive type names (e.g. "int" instead of "Integer")
            type = meta.type.kotlin.javaPrimitiveType?.simpleName ?: meta.type.simpleName ?: "Unknown",
            label = meta.label,
            description = meta.description,
            default = meta.default,
            secret = meta.isSecret,
            required = meta.isRequired,
            allowedValues = meta.allowedValues?.map { it.toString() }
        )

        /**
         * Converts the string representation of a setting to its declared type.
         * @throws IllegalArgumentException if the value is not valid
         */
        fun convert(meta: ConfigMetadata<*>, value: String): Serializable {
            val type = meta.type.kotlin.javaObjectType
            if (type.isEnum) {
                return type.enumConstants.firstOrNull { (it as Enum<*>).name == value } as Serializable?
                    ?: throw IllegalArgumentException(
                        "${meta.label} must be one of ${type.enumConstants.joinToString(", ")}"
                    )
            }
            return try {
                when (type) {
                    java.lang.Integer::class.java -> value.trim().toInt()
                    java.lang.Long::class.java -> value.trim().toLong()
                    java.lang.Float::class.java -> value.trim().toFloat()
                    java.lang.Double::class.java -> value.trim().toDouble()
                    java.lang.Boolean::class.java -> value.trim().toBooleanStrict()
                    String::class.java -> value
                    else -> throw IllegalArgumentException("Unsupported type ${type.simpleName}")
                }
            } catch (_: Exception) {
                throw IllegalArgumentException("${meta.label} must be of type ${meta.type.simpleName}")
            }
        }
    }

    /** Returns the raw (string) values of all configured settings of a task */
    fun getRaw(definition: TaskDefinition): Map<String, String?> {
        val stored = settingRepository.findAllByIdTaskKey(definition.key).associate { it.id.settingKey to it.value }
        return definition.settings.associate { meta -> meta.key to (stored[meta.key] ?: meta.default?.toString()) }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Serializable> get(definition: TaskDefinition, key: String): T? {
        val meta = definition.settings.find { it.key == key }
            ?: throw IllegalArgumentException("Unknown setting '$key' of task ${definition.key}")
        val stored = settingRepository.findById(TaskSettingKey(definition.key, key)).orElse(null)?.value
        if (stored.isNullOrEmpty()) return meta.default as T?
        return try {
            convert(meta, stored) as T
        } catch (_: IllegalArgumentException) {
            meta.default as T?
        }
    }

    /**
     * Validates the given settings.
     * @return the validation errors per setting, empty if all settings are valid
     */
    fun validate(definition: TaskDefinition, settings: Map<String, String?>): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        settings.keys.filter { key -> definition.settings.none { it.key == key } }
            .forEach { errors[it] = "Unknown setting" }
        for (meta in definition.settings) {
            val value = settings[meta.key]
            if (value.isNullOrEmpty()) {
                if (meta.isRequired && meta.default == null) errors[meta.key] = "${meta.label} is required"
                continue
            }
            try {
                convert(meta, value)
            } catch (e: IllegalArgumentException) {
                errors[meta.key] = e.message ?: "Invalid value"
            }
        }
        return errors
    }

    @Transactional
    fun update(definition: TaskDefinition, settings: Map<String, String?>) {
        val errors = validate(definition, settings)
        require(errors.isEmpty()) { "Invalid settings: ${errors.values.joinToString("; ")}" }
        settings.forEach { (key, value) ->
            val id = TaskSettingKey(definition.key, key)
            if (value.isNullOrEmpty()) {
                settingRepository.deleteById(id)
            } else {
                settingRepository.save(TaskSettingEntry(id, value))
            }
        }
    }
}
