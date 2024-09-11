package de.grimsi.gameyfin.config

import java.io.Serializable
import kotlin.reflect.KClass

sealed class ConfigProperties<T : Serializable>(
    val type: KClass<T>,
    val key: String,
    val description: String,
    val default: T? = null
) {

    /** Libraries */
    data object LibraryAllowPublicAccess :
        ConfigProperties<Boolean>(
            Boolean::class,
            "library.allow-public-access",
            "Allow access to game libraries without login",
            false
        )

    data object LibraryEnableFilesystemWatcher :
        ConfigProperties<Boolean>(
            Boolean::class,
            "library.scan.enable-filesystem-watcher",
            "Enable automatic library scanning using file system watchers",
            true
        )

    data object LibraryMetadataUpdateEnabled :
        ConfigProperties<Boolean>(
            Boolean::class,
            "library.metadata.update.enabled",
            "Enable periodic refresh of video game metadata",
            true
        )

    data object LibraryMetadataUpdateSchedule :
        ConfigProperties<String>(
            String::class,
            "library.metadata.update.schedule",
            "Schedule for periodic metadata refresh in cron format",
            "0 0 * * 0"
        )

    /** User management */
    data object UsersAllowNewSignUps : ConfigProperties<Boolean>(
        Boolean::class,
        "users.sign-ups.allow",
        "Allow new users to sign up by themselves",
        false
    )

    data object UsersConfirmNewSignUps :
        ConfigProperties<Boolean>(
            Boolean::class,
            "users.sign-ups.confirm",
            "Admins need to confirm new users",
            false
        )

    /** Notifications */
    data object NotificationsEmailHost :
        ConfigProperties<String>(String::class, "notifications.email.host", "URL of the email server")

    data object NotificationsEmailPort :
        ConfigProperties<String>(String::class, "notifications.email.port", "Port of the email server")

    data object NotificationsEmailUsername :
        ConfigProperties<String>(String::class, "notifications.email.username", "Username for the email account")

    data object NotificationsEmailPassword :
        ConfigProperties<String>(String::class, "notifications.email.password", "Password for the email account")
}