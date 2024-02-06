package de.grimsi.gameyfin.setup

import de.grimsi.gameyfin.config.Roles
import de.grimsi.gameyfin.users.entities.Role
import de.grimsi.gameyfin.users.entities.User
import de.grimsi.gameyfin.users.persistence.RoleRepository
import de.grimsi.gameyfin.users.persistence.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service


@Service
class SetupDataLoader(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder
) {
    private val log = KotlinLogging.logger {}

    @Transactional
    @EventListener(ApplicationReadyEvent::class)
    fun setupRoles() {

        createRoleIfNotFound("ROLE_ADMIN")
        createRoleIfNotFound("ROLE_USER")

        val adminRole: Role = roleRepository.findByRolename(Roles.ADMIN.roleName)!!
        val userRole: Role = roleRepository.findByRolename(Roles.USER.roleName)!!

        val admin = User("admin")
        admin.password = passwordEncoder.encode("admin")
        admin.roles = listOf(adminRole)

        val user = User("user")
        user.password = passwordEncoder.encode("user")
        user.roles = listOf(userRole)

        userRepository.saveAll(listOf(admin, user))

        log.info { "Role setup completed." }
    }

    @Transactional
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