package org.gameyfin.app.config

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
    sealed class Libraries {
        data object AllowPublicAccess : ConfigProperties<Boolean>(
            Boolean::class,
            "library.allow-public-access",
            "Allow access to Gameyfin without login",
            false
        )

        sealed class Scan {
            data object EnableFilesystemWatcher : ConfigProperties<Boolean>(
                Boolean::class,
                "library.scan.enable-filesystem-watcher",
                "Enable automatic library scanning using file system watchers (coming soon™)",
                false
            )

            data object ScanEmptyDirectories : ConfigProperties<Boolean>(
                Boolean::class,
                "library.scan.scan-empty-directories",
                "Scan empty directories",
                false
            )

            data object ExtractTitleUsingRegex : ConfigProperties<Boolean>(
                Boolean::class,
                "library.scan.extract-title-using-regex",
                "Extract title from file names using regex",
                false
            )

            data object TitleExtractionRegex : ConfigProperties<String>(
                String::class,
                "library.scan.title-extraction-regex",
                "Regex to extract title from file names",
                "^[^\\[]+"
            )

            data object TitleMatchMinRatio : ConfigProperties<Int>(
                Int::class,
                "library.scan.title-match-min-ratio",
                "Minimum ratio for title matching (0-100). Higher values mean stricter matching.",
                90
            )

            data object GameFileExtensions : ConfigProperties<Array<String>>(
                Array<String>::class,
                "library.scan.game-file-extensions",
                "File extensions to consider as games",
                arrayOf(
                    "zip",
                    "tar",
                    "gz",
                    "rar",
                    "7z",
                    "bz2",
                    "xz",
                    "iso",
                    "jar",
                    "tgz",
                    "exe",
                    "bat",
                    "cmd",
                    "com",
                    "msi",
                    "bin",
                    "run",
                    "app",
                    "dmg",
                    "elf"
                )
            )
        }

        sealed class Metadata {
            data object UpdateEnabled : ConfigProperties<Boolean>(
                Boolean::class,
                "library.metadata.update.enabled",
                "Enable periodic refresh of video game metadata",
                true
            )

            data object UpdateSchedule : ConfigProperties<String>(
                String::class,
                "library.metadata.update.schedule",
                "Schedule for periodic metadata refresh in Spring cron format",
                "@daily"
            )
        }
    }

    /** Requests */
    sealed class Requests {
        sealed class Games {
            data object Enabled : ConfigProperties<Boolean>(
                Boolean::class,
                "requests.games.enabled",
                "Enable submission of game requests",
                true
            )

            data object AllowGuestsToRequestGames : ConfigProperties<Boolean>(
                Boolean::class,
                "requests.games.allow-guests-to-request-games",
                "Allow guests (not logged in) to create game requests",
                false
            )

            data object MaxOpenRequestsPerUser : ConfigProperties<Int>(
                Int::class,
                "requests.games.max-open-requests-per-user",
                "Maximum number of pending requests per user. Set to 0 for unlimited.",
                10
            )
        }
    }

    /** User management */
    sealed class Users {
        sealed class SignUps {
            data object Allow : ConfigProperties<Boolean>(
                Boolean::class,
                "users.sign-ups.allow",
                "Allow new users to sign up by themselves",
                false
            )

            data object ConfirmationRequired : ConfigProperties<Boolean>(
                Boolean::class,
                "users.sign-ups.confirmation-required",
                "Admins need to confirm new users",
                true
            )
        }
    }

    /** Single Sign-On */
    sealed class SSO {
        sealed class OIDC {
            data object Enabled : ConfigProperties<Boolean>(
                Boolean::class,
                "sso.oidc.enabled",
                "Enable SSO via OIDC/OAuth2",
                false
            )

            data object MatchExistingUsersBy : ConfigProperties<MatchUsersBy>(
                MatchUsersBy::class,
                "sso.oidc.match-existing-users-by",
                "Match existing users by",
                MatchUsersBy.username,
                MatchUsersBy.entries
            )

            data object AutoRegisterNewUsers : ConfigProperties<Boolean>(
                Boolean::class,
                "sso.oidc.auto-register-new-users",
                "Automatically create new users after registration",
                true
            )

            data object RolesClaim : ConfigProperties<String>(
                String::class,
                "sso.oidc.roles-claim",
                "JWT claim to extract roles from",
                "roles"
            )

            data object OAuthScopes : ConfigProperties<Array<String>>(
                Array<String>::class,
                "sso.oidc.oauth-scopes",
                "OAuth2 scopes to request",
                arrayOf("openid", "profile", "email", "roles")
            )

            data object ClientId : ConfigProperties<String>(
                String::class,
                "sso.oidc.client-id",
                "Client ID"
            )

            data object ClientSecret : ConfigProperties<String>(
                String::class,
                "sso.oidc.client-secret",
                "Client secret"
            )

            data object IssuerUrl : ConfigProperties<String>(
                String::class,
                "sso.oidc.issuer-url",
                "Issuer URL"
            )

            data object AuthorizeUrl : ConfigProperties<String>(
                String::class,
                "sso.oidc.authorize-url",
                "Authorize URL"
            )

            data object TokenUrl : ConfigProperties<String>(
                String::class,
                "sso.oidc.token-url",
                "Token URL"
            )

            data object UserInfoUrl : ConfigProperties<String>(
                String::class,
                "sso.oidc.userinfo-url",
                "Userinfo URL"
            )

            data object JwksUrl : ConfigProperties<String>(
                String::class,
                "sso.oidc.jwks-url",
                "JWKS URL"
            )

            data object LogoutUrl : ConfigProperties<String>(
                String::class,
                "sso.oidc.logout-url",
                "Logout URL"
            )
        }
    }

    /** Messages */
    sealed class Messages {
        sealed class Providers {
            sealed class Email {
                data object Enabled : ConfigProperties<Boolean>(
                    Boolean::class,
                    "messages.providers.email.enabled",
                    "Enable E-Mail notifications",
                    false
                )

                data object Host : ConfigProperties<String>(
                    String::class,
                    "messages.providers.email.host",
                    "URL of the email server"
                )

                data object Port : ConfigProperties<Int>(
                    Int::class,
                    "messages.providers.email.port",
                    "Port of the email server",
                    587
                )

                data object Username : ConfigProperties<String>(
                    String::class,
                    "messages.providers.email.username",
                    "Username for the email account"
                )

                data object Password : ConfigProperties<String>(
                    String::class,
                    "messages.providers.email.password",
                    "Password for the email account"
                )
            }
        }
    }

    /** Logs */
    sealed class Logs {
        data object Folder : ConfigProperties<String>(
            String::class,
            "logs.folder",
            "Storage folder for log files",
            "./logs"
        )

        data object MaxHistoryDays : ConfigProperties<Int>(
            Int::class,
            "logs.max-history-days",
            "Log retention in days",
            30
        )

        sealed class Level {
            data object Gameyfin : ConfigProperties<LogLevel>(
                LogLevel::class,
                "logs.level.gameyfin",
                "Log level (Gameyfin)",
                LogLevel.INFO,
                LogLevel.entries
            )

            data object Root : ConfigProperties<LogLevel>(
                LogLevel::class,
                "logs.level.root",
                "Log level (Root)",
                LogLevel.WARN,
                LogLevel.entries
            )
        }
    }
}

enum class MatchUsersBy {
    username, email
}