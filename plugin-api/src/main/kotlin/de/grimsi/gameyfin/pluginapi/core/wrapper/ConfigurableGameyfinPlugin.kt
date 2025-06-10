package de.grimsi.gameyfin.pluginapi.core.wrapper

import de.grimsi.gameyfin.pluginapi.core.config.ConfigMetadata
import de.grimsi.gameyfin.pluginapi.core.config.Configurable
import de.grimsi.gameyfin.pluginapi.core.config.PluginConfigError
import de.grimsi.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import org.pf4j.PluginWrapper
import java.io.Serializable

@Suppress("UNCHECKED_CAST")
abstract class ConfigurableGameyfinPlugin(wrapper: PluginWrapper) : GameyfinPlugin(wrapper), Configurable {

    private var config: Map<String, String?> = emptyMap()

    override fun loadConfig(config: Map<String, String?>) {
        this.config = config
    }

    override fun validateConfig(): PluginConfigValidationResult = validateConfig(config)

    override fun validateConfig(config: Map<String, String?>): PluginConfigValidationResult {
        val errors = mutableMapOf<String, String>()

        for (meta in configMetadata) {
            val value = resolveValue(meta.key, config)
            if (meta.isRequired && value == null) {
                errors[meta.key] = "${meta.label} is required"
                continue
            }
            if (value != null) {
                try {
                    castConfigValue(meta, value)
                } catch (e: PluginConfigError) {
                    errors[meta.key] = e.message ?: "Invalid value"
                }
            }
        }

        return if (errors.isEmpty()) {
            PluginConfigValidationResult.VALID
        } else {
            PluginConfigValidationResult.INVALID(errors)
        }
    }

    override fun <T : Serializable> optionalConfig(key: String): T? {
        val meta = resolveMetadata(key)
        val value = resolveValue(key)
        if (value == null) return null

        return try {
            castConfigValue(meta, value) as T
        } catch (e: Exception) {
            throw PluginConfigError("Failed to cast value for key '$key' to type ${meta.type.simpleName}: ${e.message}")
        }
    }

    private fun castConfigValue(meta: ConfigMetadata<*>, value: Any): Any? {
        val expectedType = meta.type

        // Handle enums
        if (expectedType.isEnum) {
            try {
                return java.lang.Enum.valueOf(expectedType as Class<out Enum<*>>, value.toString())
            } catch (_: IllegalArgumentException) {
                throw PluginConfigError("Invalid value '${value}', must be one of ${meta.allowedValues!!.joinToString(", ")}")
            }
        }

        // If already correct type
        if (expectedType.isInstance(value)) return value

        // Try to convert common types
        try {
            return when (expectedType) {
                Int::class.java, Integer::class.java -> value.toString().toInt()
                Float::class.java, java.lang.Float::class.java -> value.toString().toFloat()
                Double::class.java, java.lang.Double::class.java -> value.toString().toDouble()
                Long::class.java, java.lang.Long::class.java -> value.toString().toLong()
                Boolean::class.java, java.lang.Boolean::class.java -> value.toString().toBooleanStrict()
                String::class.java -> value.toString()
                else -> {
                    // Try valueOf(String) or parse(String) via reflection
                    val method = expectedType.methods.find {
                        (it.name == "valueOf" || it.name == "parse") &&
                                it.parameterTypes.size == 1 &&
                                it.parameterTypes[0] == String::class.java
                    }
                    if (method != null) {
                        method.invoke(null, value.toString())
                    } else {
                        throw IllegalArgumentException()
                    }
                }
            }
        } catch (_: Exception) {
            throw PluginConfigError("Value must be of type ${expectedType.simpleName}")
        }
    }

    override fun <T : Serializable> config(key: String): T {
        val value = optionalConfig<T>(key)
        if (value == null) {
            throw PluginConfigError("Required configuration key '$key' is missing or has no value")
        }
        return value
    }

    private fun resolveMetadata(key: String): ConfigMetadata<*> {
        return configMetadata.find { it.key == key }
            ?: throw PluginConfigError("Unknown configuration key: $key")
    }

    private fun resolveValue(key: String, configOverride: Map<String, Serializable?>? = null): Serializable? {
        val meta = resolveMetadata(key)
        val conf = configOverride ?: config
        return conf[key] ?: meta.default
    }
}