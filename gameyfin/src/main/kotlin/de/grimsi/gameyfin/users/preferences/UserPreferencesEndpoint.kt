package de.grimsi.gameyfin.users.preferences

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.PermitAll

@Endpoint
@PermitAll
class UserPreferencesEndpoint(
    private val userPreferences: UserPreferencesService
) {
    fun get(key: String): String? {
        return userPreferences.get(key)
    }

    fun set(key: String, value: String) {
        userPreferences.set(key, value)
    }
}