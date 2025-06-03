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
        return if (expectedType.isEnum) {
            try {
                java.lang.Enum.valueOf(expectedType as Class<out Enum<*>>, value.toString())
            } catch (_: IllegalArgumentException) {
                throw PluginConfigError("Invalid value '${value}', must be one of ${meta.allowedValues!!.joinToString(", ")}")
            }
        } else {
            if (!expectedType.isInstance(value)) {
                throw PluginConfigError("Value for key '${meta.key}' is not of type ${expectedType.simpleName}")
            }
            value
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