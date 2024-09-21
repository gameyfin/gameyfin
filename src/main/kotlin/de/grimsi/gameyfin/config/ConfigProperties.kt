package de.grimsi.gameyfin.config

import org.springframework.boot.logging.LogLevel
import java.io.Serializable
import kotlin.reflect.KClass

sealed class ConfigProperties<T : Serializable>(
    val type: KClass<T>,
    val key: String,
    val description: String,
    val default: T? = null,
    val allowedValues: List<T>? = null
) {

    /** Libraries */
    data object LibraryAllowPublicAccess : ConfigProperties<Boolean>(
        Boolean::class,
        "library.allow-public-access",
        "Allow access to game libraries without login",
        false
    )

    data object LibraryEnableFilesystemWatcher : ConfigProperties<Boolean>(
        Boolean::class,
        "library.scan.enable-filesystem-watcher",
        "Enable automatic library scanning using file system watchers",
        true
    )

    data object LibraryMetadataUpdateEnabled : ConfigProperties<Boolean>(
        Boolean::class,
        "library.metadata.update.enabled",
        "Enable periodic refresh of video game metadata",
        true
    )

    data object LibraryMetadataUpdateSchedule : ConfigProperties<String>(
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

    data object UsersConfirmNewSignUps : ConfigProperties<Boolean>(
        Boolean::class,
        "users.sign-ups.confirm",
        "Admins need to confirm new users",
        false
    )

    /** Single Sign-On */
    data object SsoEnabled : ConfigProperties<Boolean>(
        Boolean::class,
        "sso.oidc.enabled",
        "Enable SSO via OIDC/OAuth2",
        false
    )

    data object SsoClientId : ConfigProperties<String>(
        String::class,
        "sso.oidc.client-id",
        "Client ID"
    )

    data object SsoClientSecret : ConfigProperties<String>(
        String::class,
        "sso.oidc.client-secret",
        "Client secret"
    )

    data object SsoIssuerUrl : ConfigProperties<String>(
        String::class,
        "sso.oidc.issuer-url",
        "Issuer URL"
    )

    data object SsoAuthorizeUrl : ConfigProperties<String>(
        String::class,
        "sso.oidc.authorize-url",
        "Authorize URL"
    )

    data object SsoTokenUrl : ConfigProperties<String>(
        String::class,
        "sso.oidc.token-url",
        "Token URL"
    )

    data object SsoUserInfoUrl : ConfigProperties<String>(
        String::class,
        "sso.oidc.userinfo-url",
        "Userinfo URL"
    )

    data object SsoJwksUrl : ConfigProperties<String>(
        String::class,
        "sso.oidc.jwks-url",
        "JWKS URL"
    )

    data object SsoLogoutUrl : ConfigProperties<String>(
        String::class,
        "sso.oidc.logout-url",
        "Logout URL"
    )

    data object SsoMatchExistingUsersBy : ConfigProperties<MatchUsersBy>(
        MatchUsersBy::class,
        "sso.oidc.match-existing-users-by",
        "Match existing users by",
        MatchUsersBy.username,
        MatchUsersBy.entries
    )

    data object SsoAutoRegisterNewUsers : ConfigProperties<Boolean>(
        Boolean::class,
        "sso.oidc.auto-register-new-users",
        "Automatically create new users after registration",
        true
    )

    /** Notifications */
    data object NotificationsEnabled : ConfigProperties<Boolean>(
        Boolean::class,
        "notifications.enabled",
        "Enable notifications",
        false
    )

    data object NotificationsEmailHost : ConfigProperties<String>(
        String::class,
        "notifications.providers.email.host",
        "URL of the email server"
    )

    data object NotificationsEmailPort : ConfigProperties<Int>(
        Int::class,
        "notifications.providers.email.port",
        "Port of the email server",
        587
    )

    data object NotificationsEmailUsername : ConfigProperties<String>(
        String::class,
        "notifications.providers.email.username",
        "Username for the email account"
    )

    data object NotificationsEmailPassword : ConfigProperties<String>(
        String::class,
        "notifications.providers.email.password",
        "Password for the email account"
    )

    data object NotificationsTemplateNewUser : ConfigProperties<String>(
        String::class,
        "notifications.templates.new-user",
        "Template for new user notifications"
    )

    data object NotificationsTemplateNewInvite : ConfigProperties<String>(
        String::class,
        "notifications.templates.new-invite",
        "Template for new user notifications"
    )

    data object NotificationsTemplateNewPasswordRequest : ConfigProperties<String>(
        String::class,
        "notifications.templates.new-password-request",
        "Template for new password request notifications"
    )

    data object NotificationsTemplateNewGame : ConfigProperties<String>(
        String::class,
        "notifications.templates.new-game",
        "Template for new game notifications"
    )

    data object NotificationsTemplateNewGameRequest : ConfigProperties<String>(
        String::class,
        "notifications.templates.new-game-request",
        "Template for new game request notifications"
    )

    /** Logs */
    data object LogsFolder : ConfigProperties<String>(
        String::class,
        "logs.folder",
        "Storage folder for log files",
        "./logs"
    )

    data object LogsMaxHistoryDays : ConfigProperties<Int>(
        Int::class,
        "logs.max-history-days",
        "Log retention in days",
        30
    )

    data object LogsLevel : ConfigProperties<LogLevel>(
        LogLevel::class,
        "logs.level",
        "Log level",
        LogLevel.INFO,
        LogLevel.entries
    )
}

enum class MatchUsersBy {
    username, email
}