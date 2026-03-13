package org.gameyfin.app.config

import org.springframework.boot.logging.LogLevel
import java.io.Serializable
import kotlin.reflect.KClass

sealed class ConfigProperties<T : Serializable>(
    val type: KClass<T>,
    val key: String,
    val name: String,
    val description: String,
    val default: T? = null,
    val allowedValues: List<T>? = null,
    val min: Number? = null,
    val max: Number? = null,
    val step: Number? = null
) {

    /** Security */
    sealed class Security {
        data object AllowPublicAccess : ConfigProperties<Boolean>(
            Boolean::class,
            "security.allow-public-access",
            "Allow access to Gameyfin without login",
            "When enabled, anyone can browse (and potentially download) games **without logging in**.",
            false
        )
    }

    /** Libraries */
    sealed class Libraries {
        sealed class Scan {
            data object EnableFilesystemWatcher : ConfigProperties<Boolean>(
                Boolean::class,
                "library.scan.enable-filesystem-watcher",
                "Enable automatic library scanning using file system watchers",
                "Watches your library folders for file changes and **updates this specific folder** when files are added, removed, or renamed.",
                false
            )

            data object ScanEmptyDirectories : ConfigProperties<Boolean>(
                Boolean::class,
                "library.scan.scan-empty-directories",
                "Scan empty directories",
                "When enabled, empty folders inside a library path are also reported during a scan.",
                false
            )

            data object ExtractTitleUsingRegex : ConfigProperties<Boolean>(
                Boolean::class,
                "library.scan.extract-title-using-regex",
                "Extract title from file names using regex",
                "Uses the regex defined in **Title Extraction Regex** to strip unwanted parts (e.g. release tags) from file names before matching.",
                false
            )

            data object TitleExtractionRegex : ConfigProperties<String>(
                String::class,
                "library.scan.title-extraction-regex",
                "Regex to extract title from file names",
                "Java-compatible regular expression used to extract the game title from a file name. The first captured group (or full match) is used as the title.",
                "^[^\\[]+"
            )

            data object TitleMatchMinRatio : ConfigProperties<Int>(
                Int::class,
                "library.scan.title-match-min-ratio",
                "Minimum ratio for title matching. Higher values mean stricter matching.",
                """Used to match titles **across different metadata sources (plugins)**.
                    |Raise this value to reduce false positives; lower it to match more liberally.""".trimMargin(),
                default = 90,
                min = 0,
                max = 100,
                step = 1
            )

            data object GameFileExtensions : ConfigProperties<Array<String>>(
                Array<String>::class,
                "library.scan.game-file-extensions",
                "File extensions to consider as games",
                "Only files whose extension appears in this list are treated as games during a library scan. Add custom extensions to support additional formats.",
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

            data object MaxConcurrency : ConfigProperties<Int>(
                Int::class,
                "library.scan.max-concurrency",
                "Scan concurrency",
                """Controls how many games are processed simultaneously during a library scan (metadata fetching, image downloading, etc.).
                    |Lower values reduce peak memory usage; higher values speed up large scans.
                    |Does **not** affect already running scans.""".trimMargin(),
                default = 4,
                min = 1,
                max = 16,
                step = 1
            )
        }

        sealed class Metadata {
            data object UpdateEnabled : ConfigProperties<Boolean>(
                Boolean::class,
                "library.metadata.update.enabled",
                "Enable periodic refresh of video game metadata",
                "When enabled, Gameyfin periodically re-fetches metadata (cover art, descriptions, genres, …) according to the configured schedule.",
                true
            )

            data object UpdateSchedule : ConfigProperties<String>(
                String::class,
                "library.metadata.update.schedule",
                "Schedule for periodic metadata refresh in Spring cron format",
                "Controls **when** the automatic metadata refresh runs. Accepts [Spring cron expressions](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/support/CronExpression.html) or shortcuts such as `@daily`.",
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
                "Allows users to submit requests for games they would like to see added to the library.",
                true
            )

            data object AllowGuestsToRequestGames : ConfigProperties<Boolean>(
                Boolean::class,
                "requests.games.allow-guests-to-request-games",
                "Allow guests (not logged in) to create game requests",
                "When enabled, visitors who are **not logged in** can also submit game requests.",
                false
            )

            data object MaxOpenRequestsPerUser : ConfigProperties<Int>(
                Int::class,
                "requests.games.max-open-requests-per-user",
                "Maximum number of pending requests per user. Set to 0 for unlimited.",
                "Caps the number of **open (unresolved) requests** a single user can have at any time. Set to `0` to remove the limit.",
                10
            )
        }
    }

    /** Downloads */
    sealed class Downloads {
        data object BandwidthLimitEnabled : ConfigProperties<Boolean>(
            Boolean::class,
            "downloads.bandwidth-limit.enabled",
            "Enable per-user bandwidth limiting for downloads",
            "When enabled, each user's download speed is capped at the value specified in **Bandwidth Limit (Mbps)**.",
            false
        )

        data object BandwidthLimitMbps : ConfigProperties<Int>(
            Int::class,
            "downloads.bandwidth-limit.mbps",
            "Maximum download speed in Megabits per second (Mbps)",
            "The maximum allowed download speed **per user** in Megabits per second. Only takes effect when bandwidth limiting is enabled.",
            100
        )
    }

    /** User management */
    sealed class Users {
        sealed class SignUps {
            data object Allow : ConfigProperties<Boolean>(
                Boolean::class,
                "users.sign-ups.allow",
                "Allow new users to sign up by themselves",
                "When enabled, a **Register** button is shown on the login page so anyone can create an account.",
                false
            )

            data object ConfirmationRequired : ConfigProperties<Boolean>(
                Boolean::class,
                "users.sign-ups.confirmation-required",
                "Admins need to confirm new users",
                "When enabled, newly registered accounts are **inactive** until an administrator explicitly approves them.",
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
                "Activates the **OpenID Connect / OAuth 2.0** single sign-on integration. All other OIDC settings below are required when this is turned on.",
                false
            )

            data object MatchExistingUsersBy : ConfigProperties<MatchUsersBy>(
                MatchUsersBy::class,
                "sso.oidc.match-existing-users-by",
                "Match existing users by",
                "Determines which field (`username` or `email`) is used to link an incoming SSO identity to an **existing Gameyfin account**.",
                MatchUsersBy.username,
                MatchUsersBy.entries
            )

            data object UsernameClaim : ConfigProperties<String>(
                String::class,
                "sso.oidc.username-claim",
                "Username claim",
                """Name of the OIDC / userinfo claim used as the Gameyfin username (e.g. `preferred_username`, `name`, `email`).
                    |If the claim is absent or blank, Gameyfin falls back through `preferred_username` → `nickname` → `name` → `email` → `sub` automatically.""".trimMargin(),
                "preferred_username"
            )

            data object RolesClaim : ConfigProperties<String>(
                String::class,
                "sso.oidc.roles-claim",
                "Role claim",
                """Name of the OIDC / userinfo claim that contains the user's roles.
                    |Gameyfin maps these roles to its own permission system.""".trimMargin(),
                "roles"
            )

            data object OAuthScopes : ConfigProperties<Array<String>>(
                Array<String>::class,
                "sso.oidc.oauth-scopes",
                "OAuth2 scopes to request",
                "List of [OAuth 2.0 scopes](https://oauth.net/2/scope/) sent in the authorization request. Must include at least `openid`.",
                arrayOf("openid", "profile", "email", "roles")
            )

            data object ClientId : ConfigProperties<String>(
                String::class,
                "sso.oidc.client-id",
                "Client ID",
                "The **client identifier** issued by your identity provider when you registered Gameyfin as an OAuth 2.0 application."
            )

            data object ClientSecret : ConfigProperties<String>(
                String::class,
                "sso.oidc.client-secret",
                "Client secret",
                "The **client secret** issued by your identity provider. Keep this value confidential."
            )

            data object IssuerUrl : ConfigProperties<String>(
                String::class,
                "sso.oidc.issuer-url",
                "Issuer URL",
                "The base URL of your identity provider (e.g. `https://auth.example.com/realms/myrealm`). Used for OIDC discovery."
            )

            data object AuthorizeUrl : ConfigProperties<String>(
                String::class,
                "sso.oidc.authorize-url",
                "Authorize URL",
                "The **authorization endpoint** of your identity provider. Required when OIDC auto-discovery is not available."
            )

            data object TokenUrl : ConfigProperties<String>(
                String::class,
                "sso.oidc.token-url",
                "Token URL",
                "The **token endpoint** used to exchange an authorization code for access and ID tokens."
            )

            data object UserInfoUrl : ConfigProperties<String>(
                String::class,
                "sso.oidc.userinfo-url",
                "Userinfo URL",
                "The **userinfo endpoint** from which Gameyfin retrieves profile claims (name, email, roles, …) after a successful login."
            )

            data object JwksUrl : ConfigProperties<String>(
                String::class,
                "sso.oidc.jwks-url",
                "JWKS URL",
                "The **JSON Web Key Set endpoint** used to verify the signature of JWTs issued by your identity provider."
            )

            data object LogoutUrl : ConfigProperties<String>(
                String::class,
                "sso.oidc.logout-url",
                "Logout URL",
                "The **end-session endpoint** to which Gameyfin redirects users after they log out, ensuring they are also signed out from the identity provider."
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
                    "When enabled, Gameyfin can send **e-mail notifications** (e.g. sign-up confirmations, request updates) via the configured SMTP server.",
                    false
                )

                data object Host : ConfigProperties<String>(
                    String::class,
                    "messages.providers.email.host",
                    "URL of the email server",
                    "Hostname or IP address of the **SMTP server** used to dispatch outgoing e-mails (e.g. `smtp.gmail.com`)."
                )

                data object Port : ConfigProperties<Int>(
                    Int::class,
                    "messages.providers.email.port",
                    "Port of the email server",
                    "TCP port of the SMTP server. Common values: `587` (STARTTLS), `465` (SSL/TLS), `25` (unencrypted).",
                    587
                )

                data object Username : ConfigProperties<String>(
                    String::class,
                    "messages.providers.email.username",
                    "Username for the email account",
                    "The username (usually the full e-mail address) used to **authenticate** with the SMTP server."
                )

                data object Password : ConfigProperties<String>(
                    String::class,
                    "messages.providers.email.password",
                    "Password for the email account",
                    "The password used to **authenticate** with the SMTP server. Keep this value confidential."
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
            "Path to the directory where Gameyfin writes its **log files**. Can be absolute or relative to the working directory.",
            "./logs"
        )

        data object MaxHistoryDays : ConfigProperties<Int>(
            Int::class,
            "logs.max-history-days",
            "Log retention in days",
            "Number of days log files are kept before being **automatically deleted**. Set to `0` to disable automatic clean-up.",
            30
        )

        sealed class Level {
            data object Gameyfin : ConfigProperties<LogLevel>(
                LogLevel::class,
                "logs.level.gameyfin",
                "Log level (Gameyfin)",
                "Minimum severity level for Gameyfin's own log messages. Use `DEBUG` or `TRACE` for detailed troubleshooting output.",
                LogLevel.INFO,
                LogLevel.entries
            )

            data object Root : ConfigProperties<LogLevel>(
                LogLevel::class,
                "logs.level.root",
                "Log level (Root)",
                "Minimum severity level for **all other libraries and frameworks** (Spring, Hibernate, …). It is recommended to keep this at `WARN` or `ERROR` in production.",
                LogLevel.WARN,
                LogLevel.entries
            )
        }
    }
}

@Suppress("EnumEntryName")
enum class MatchUsersBy {
    username, email
}