package org.gameyfin.app.users.preferences

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.users.UserService
import org.springframework.stereotype.Service
import java.io.Serializable

@Service
class UserPreferencesService(
    private val userPreferenceRepository: UserPreferenceRepository,
    private val userService: UserService
) {
    private val log = KotlinLogging.logger {}

    /**
     * Get the current value of a user preference in a type-safe way.
     * Used internally.
     *
     * @param userPreference: The user preference containing necessary type information
     * @return The current value if set or the default value or null if no value is set and no default value exists
     */
    fun <T : Serializable> get(userPreference: UserPreferences<T>): T? {
        log.debug { "Getting user preference '${userPreference.key}'" }

        val id = id(userPreference.key)
        val appConfig = userPreferenceRepository.findById(id).orElse(null)

        return if (appConfig != null) {
            getValue(appConfig.value, userPreference)
        } else {
            return null
        }
    }

    /**
     * Get the current value of a user preference in a *not* type-safe way.
     * Used for the external API.
     *
     * @param key: The key of the user preference
     * @return The current value if set or the default value or null if no value is set and no default value exists
     */
    fun get(key: String): String? {

        log.debug { "Getting user preference '$key'" }

        val userPreference = findUserPreference(key)
        val id = id(key)
        val appConfig = userPreferenceRepository.findById(id).orElse(null)

        return if (appConfig != null) {
            getValue(appConfig.value, userPreference).toString()
        } else {
            return null
        }
    }

    /**
     * Set the value for a specified key in a type-safe way.
     *
     * @param userPreference: The target user preference
     * @param value: Value to set the user preference to
     * @throws IllegalArgumentException if the value can't be cast to the type defined for the user preference
     */
    fun <T : Serializable> set(userPreference: UserPreferences<T>, value: T) {
        return set(userPreference.key, value)
    }

    /**
     * Set the value for a specified key.
     * Checks if the value can be cast to the type defined for the user preference.
     *
     * @param key: Key of the target user preference
     * @param value: Value to set the user preference to
     * @throws IllegalArgumentException if the value can't be cast to the type defined for the user preference
     */
    fun <T : Serializable> set(key: String, value: T) {
        log.debug { "Set user preference '$key'" }

        val userPreferenceKey = findUserPreference(key)

        // Check if the value can be cast to the type defined for the user preference
        val castedValue = getValue(value.toString(), userPreferenceKey)

        val id = id(key)
        var userPreference = userPreferenceRepository.findById(id).orElse(null)

        if (userPreference == null) {
            userPreference = UserPreference(id, castedValue.toString())
        } else {
            userPreference.value = castedValue.toString()
        }

        try {
            userPreferenceRepository.save(userPreference)
        } catch (e: Exception) {
            log.warn { "Error saving user preference '$key': ${e.message}" }
        }
    }

    /**
     * Get the value of the user preference in a type-safe way.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Serializable> getValue(value: String, userPreference: UserPreferences<T>): T {
        return when (userPreference.type) {
            String::class -> value as T
            Boolean::class -> value.toBoolean() as T
            Int::class -> value.toFloat().toInt() as T
            Float::class -> value.toFloat() as T
            else -> {
                if (userPreference.type.java.isEnum) {
                    val enumConstants = userPreference.type.java.enumConstants
                    enumConstants.firstOrNull { it.toString() == value }
                        ?: throw IllegalArgumentException("Unknown enum value '$value' for key ${userPreference.key}")
                } else {
                    throw IllegalArgumentException("Unknown config type ${userPreference.type}: '$value' for key ${userPreference.key}")
                }
            }
        }
    }

    /**
     * Returns a user preference
     */
    private fun findUserPreference(key: String): UserPreferences<*> {
        // Use reflection to get all objects defined within ConfigKey
        val configProperties = UserPreferences::class.sealedSubclasses.flatMap { subclass ->
            subclass.objectInstance?.let { listOf(it) } ?: listOf()
        }

        // Find the matching config key based on the string key
        return configProperties.find { it.key == key }
            ?: throw IllegalArgumentException("Unknown user preference key: $key")
    }

    private fun id(key: String): UserPreferenceKey {
        val auth = getCurrentAuth()
        val user = userService.getByUsernameNonNull(auth.name)
        return UserPreferenceKey(key, user.id!!)
    }
}

