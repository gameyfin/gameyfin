package de.grimsi.gameyfin.setup

import de.grimsi.gameyfin.config.Roles
import de.grimsi.gameyfin.users.UserService
import de.grimsi.gameyfin.users.entities.Role
import de.grimsi.gameyfin.users.entities.User
import de.grimsi.gameyfin.users.persistence.RoleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service


@Service
@Transactional
class SetupDataLoader(
    private val roleRepository: RoleRepository,
    private val userService: UserService
) {
    private val log = KotlinLogging.logger {}

    @EventListener(ApplicationReadyEvent::class)
    fun initialSetup() {
        log.info { "Looks like this is the first time your're starting Gameyfin." }
        log.info { "We will now set up some data..." }

        setupRoles()
        setupUsers()

        log.info { "Setup completed..." }
    }

    fun setupUsers() {
        val superadmin = User("admin")
        superadmin.password = "admin"
        superadmin.roles = listOf(roleRepository.findByRolename(Roles.SUPERADMIN.roleName)!!)
        userService.registerUser(superadmin)

        val user = User("user")
        user.password = "user"
        user.roles = listOf(roleRepository.findByRolename(Roles.USER.roleName)!!)
        userService.registerUser(user)
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
}