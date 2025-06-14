package org.gameyfin.app.users.preferences

import java.io.Serializable
import kotlin.reflect.KClass

sealed class UserPreferences<T : Serializable>(
    val type: KClass<T>,
    val key: String,
    val allowedValues: List<T>? = null
) {
    data object PreferredTheme : UserPreferences<String>(String::class, "preferred-theme")
    data object PreferredDownloadMethod : UserPreferences<String>(String::class, "preferred-download-method")
}