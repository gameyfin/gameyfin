package de.grimsi.gameyfin.users

import de.grimsi.gameyfin.meta.Roles
import de.grimsi.gameyfin.users.dto.UserInfoDto
import de.grimsi.gameyfin.users.dto.UserUpdateDto
import de.grimsi.gameyfin.users.entities.Avatar
import de.grimsi.gameyfin.users.entities.Role
import de.grimsi.gameyfin.users.entities.User
import de.grimsi.gameyfin.users.persistence.AvatarContentStore
import de.grimsi.gameyfin.users.persistence.UserRepository
import jakarta.transaction.Transactional
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream


@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val roleService: RoleService,
    private val sessionService: SessionService,
    private val avatarStore: AvatarContentStore
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userByUsername(username)

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

    fun existsByUsername(username: String): Boolean = userRepository.findByUsername(username) != null

    fun getAllUsers(): List<UserInfoDto> {
        return userRepository.findAll().map { u -> toUserInfo(u) }
    }

    fun getUserInfo(username: String): UserInfoDto {
        val user = userByUsername(username)
        return toUserInfo(user)
    }

    fun getAvatar(username: String): Avatar? {
        val user = userByUsername(username)
        return user.avatar
    }

    fun getAvatarFile(avatar: Avatar): InputStream {
        return avatarStore.getContent(avatar)
    }

    fun setAvatar(username: String, file: MultipartFile) {
        val user = userByUsername(username)

        if (user.avatar == null) {
            user.avatar = Avatar(mimeType = file.contentType)
        }

        avatarStore.setContent(user.avatar, file.inputStream)
        userRepository.save(user)
    }

    fun deleteAvatar(username: String) {
        val user = userByUsername(username)

        avatarStore.unsetContent(user.avatar)
        user.avatar = null

        userRepository.save(user)
    }

    fun registerUser(user: User, role: Roles): User {
        return registerUser(user, listOf(role))
    }

    fun registerUser(user: User, roles: List<Roles>): User {
        user.password = passwordEncoder.encode(user.password)
        user.roles = roleService.toRoles(roles)
        return userRepository.save(user)
    }

    fun updateUser(username: String, updates: UserUpdateDto) {
        val user = userByUsername(username)

        updates.username?.let { user.username = it }
        updates.password?.let { user.password = passwordEncoder.encode(it) }
        updates.email?.let { user.email = it }

        userRepository.save(user)

        // If user changes password, all sessions should be invalidated
        if (updates.password != null) {
            sessionService.logoutAllSessions()
        }
    }

    fun deleteUser(username: String) {
        val user = userByUsername(username)
        userRepository.delete(user)
    }

    fun toUserInfo(user: User): UserInfoDto {
        return UserInfoDto(
            username = user.username,
            email = user.email,
            roles = user.roles.map { r -> r.rolename }
        )
    }

    private fun toAuthorities(roles: Collection<Role>): List<GrantedAuthority> {
        return roles.map { r -> SimpleGrantedAuthority(r.rolename) }
    }

    private fun userByUsername(username: String): User {
        return userRepository.findByUsername(username) ?: throw UsernameNotFoundException("Unknown user '$username'")
    }
}