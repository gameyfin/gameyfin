package de.grimsi.gameyfin.config

import java.io.Serializable
import kotlin.reflect.KClass

sealed class ConfigProperty<T : Serializable>(val type: KClass<T>, val key: String, val default: T? = null) {

    /** Libraries */
    // Allow access to game libraries without login
    data object LibraryAllowPublicAccess :
        ConfigProperty<Boolean>(Boolean::class, "library.allow-public-access", false)

    // Enable automatic library scanning using file system watchers
    data object LibraryEnableFilesystemWatcher :
        ConfigProperty<Boolean>(Boolean::class, "library.scan.enable-filesystem-watcher", true)

    // Enable periodic refresh of video game meta-data and set the schedule (default is once per week)
    data object LibraryMetadataUpdateEnabled :
        ConfigProperty<Boolean>(Boolean::class, "library.metadata.update.enabled", true)

    data object LibraryMetadataUpdateSchedule :
        ConfigProperty<String>(String::class, "library.metadata.update.schedule", "0 0 * * 0")

    /** User management */
    // Allow new users to sign up by themselves
    data object UsersAllowNewSignUps : ConfigProperty<Boolean>(Boolean::class, "users.sign-ups.allow", false)

    // If an administrator needs to confirm new sign-ups before they are allowed to log in
    data object UsersConfirmNewSignUps :
        ConfigProperty<Boolean>(Boolean::class, "users.sign-ups.confirm", false)

    /** Notifications */
    // Settings for the mail server used by Gameyfin to send notifications
    data object NotificationsEmailHost : ConfigProperty<String>(String::class, "notifications.email.host")
    data object NotificationsEmailPort : ConfigProperty<String>(String::class, "notifications.email.port")
    data object NotificationsEmailUsername : ConfigProperty<String>(String::class, "notifications.email.username")
    data object NotificationsEmailPassword : ConfigProperty<String>(String::class, "notifications.email.password")
}