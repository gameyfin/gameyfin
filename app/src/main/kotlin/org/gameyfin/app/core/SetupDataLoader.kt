package org.gameyfin.app.core

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.setup.SetupService
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.entities.User
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.net.InetAddress


@Service
class SetupDataLoader(
    private val userService: UserService,
    private val setupService: SetupService,
    private val env: Environment
) {
    private val log = KotlinLogging.logger {}

    @EventListener(ApplicationReadyEvent::class)
    fun initialSetup() {
        if (setupService.isSetupCompleted()) return

        log.info { "Looks like this is the first time you're starting Gameyfin." }

        if ("dev" in env.activeProfiles) {
            log.info { "We will now set up some data for local development..." }
            setupUsers()
            log.info { "Setup completed..." }
        }

        val protocol = if (env.getProperty("server.ssl.key-store") != null) "https" else "http"
        val rawAppUrl = env.getProperty("app.url")
        val appUrl = when {
            rawAppUrl.isNullOrBlank() -> null
            rawAppUrl.startsWith("http://") || rawAppUrl.startsWith("https://") -> rawAppUrl
            else -> "$protocol://$rawAppUrl"
        }
        val setupUrl =
            appUrl ?: "${protocol}://${InetAddress.getLocalHost().hostName}:${env.getProperty("server.port")}/setup"
        log.info { "Visit $setupUrl to complete the setup" }
    }

    fun setupUsers() {
        log.info { "Setting up users..." }

        val superadmin = User(
            username = "admin",
            password = "admin",
            email = "admin@gameyfin.org",
            emailConfirmed = true,
            enabled = true,
            roles = listOf(Role.SUPERADMIN)
        )

        registerUserIfNotFound(superadmin)

        val user = User(
            username = "user",
            password = "user",
            email = "user@gameyfin.org",
            emailConfirmed = true,
            enabled = true,
            roles = listOf(Role.USER)
        )

        registerUserIfNotFound(user)

        log.info { "User setup completed." }
    }

    fun registerUserIfNotFound(user: User) {
        if (userService.existsByUsername(user.username)) return

        userService.registerOrUpdateUser(user)
    }
}