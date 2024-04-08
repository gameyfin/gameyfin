package de.grimsi.gameyfin.users

import de.grimsi.gameyfin.users.entities.Role
import de.grimsi.gameyfin.users.entities.User
import de.grimsi.gameyfin.users.persistence.RoleRepository
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
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder
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

    fun registerUser(user: User): User {
        user.password = passwordEncoder.encode(user.password)
        return userRepository.save(user)
    }

    fun toRoles(roles: Collection<String>): List<Role> {
        return roles.mapNotNull { r -> roleRepository.findByRolename(r) }
    }

    private fun toAuthorities(roles: Collection<Role>): List<GrantedAuthority> {
        return roles.map { r -> SimpleGrantedAuthority(r.rolename) }
    }
}