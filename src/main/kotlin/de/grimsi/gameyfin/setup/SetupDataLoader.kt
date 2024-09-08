package de.grimsi.gameyfin.setup

import de.grimsi.gameyfin.meta.Roles
import de.grimsi.gameyfin.users.UserService
import de.grimsi.gameyfin.users.entities.Role
import de.grimsi.gameyfin.users.entities.User
import de.grimsi.gameyfin.users.persistence.RoleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.net.InetAddress


@Service
@Transactional
class SetupDataLoader(
    private val roleRepository: RoleRepository,
    private val userService: UserService,
    private val env: Environment
) {
    private val log = KotlinLogging.logger {}

    @EventListener(ApplicationReadyEvent::class)
    fun initialSetup() {
        log.info { "Looks like this is the first time you're starting Gameyfin." }
        log.info { "We will now set up some data..." }

        setupRoles()

        if ("dev" in env.activeProfiles) {
            setupUsers()
        }

        log.info { "Setup completed..." }
        log.info { "Visit http://${InetAddress.getLocalHost().hostName}:${env.getProperty("server.port")}/setup to complete the setup" }
    }

    fun setupRoles() {

        log.info { "Setting up roles..." }

        createRoleIfNotFound(Roles.SUPERADMIN.roleName)
        createRoleIfNotFound(Roles.ADMIN.roleName)
        createRoleIfNotFound(Roles.USER.roleName)

        log.info { "Role setup completed." }
    }

    fun createRoleIfNotFound(name: String): Role {
        log.info { "Creating role $name" }

        var role: Role? = roleRepository.findByRolename(name)

        if (role == null) {
            role = Role(name)
            roleRepository.save(role)
        }
        return role
    }

    fun setupUsers() {
        log.info { "Setting up users..." }

        val superadmin = User(
            username = "admin",
            password = "admin"
        )

        registerUserIfNotFound(superadmin, Roles.SUPERADMIN)

        val user = User(
            username = "user",
            password = "user"
        )

        registerUserIfNotFound(user, Roles.USER)

        log.info { "User setup completed." }
    }

    fun registerUserIfNotFound(user: User, role: Roles) {
        if (userService.existsByUsername(user.username)) return

        userService.registerUser(user, role)
    }
}