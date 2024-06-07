package de.grimsi.gameyfin.users

import de.grimsi.gameyfin.config.Roles
import de.grimsi.gameyfin.users.dto.UserInfo
import de.grimsi.gameyfin.users.entities.Role
import de.grimsi.gameyfin.users.entities.User
import de.grimsi.gameyfin.users.persistence.UserRepository
import jakarta.transaction.Transactional
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service


@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val roleService: RoleService
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user =
            userRepository.findByUsername(username) ?: throw UsernameNotFoundException("Unknown user '$username'")

        return org.springframework.security.core.userdetails.User(
            user.username,
            user.password,
            user.enabled,
            true,
            true,
            true,
            toAuthorities(user.roles)
        )
    }

    fun registerUser(user: User, role: Roles): User {
        return registerUser(user, listOf(role))
    }

    fun registerUser(user: User, roles: List<Roles>): User {
        user.password = passwordEncoder.encode(user.password)
        user.roles = roleService.toRoles(roles)
        return userRepository.save(user)
    }

    fun toUserInfo(user: User): UserInfo {
        return UserInfo(
            username = user.username,
            email = user.email,
            roles = user.roles.map { r -> r.rolename }
        )
    }

    private fun toAuthorities(roles: Collection<Role>): List<GrantedAuthority> {
        return roles.map { r -> SimpleGrantedAuthority(r.rolename) }
    }
}