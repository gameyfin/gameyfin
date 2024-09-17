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
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.oidc.user.OidcUser
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

    fun existsByUsername(username: String): Boolean = userRepository.existsByUsername(username)

    fun findByOidcProviderId(oidcProviderId: String): User? = userRepository.findByOidcProviderId(oidcProviderId)

    fun getAllUsers(): List<UserInfoDto> {
        return userRepository.findAll().map { u -> toUserInfo(u) }
    }

    fun getByEmail(email: String): User? {
        return userRepository.findByEmail(email)
    }

    fun getByUsername(username: String): User? {
        return userRepository.findByUsername(username)
    }

    fun getUserInfo(auth: Authentication): UserInfoDto {
        val principal = auth.principal

        if (principal is OidcUser) {
            val oidcUser = User(principal)
            val userInfoDto = toUserInfo(oidcUser)
            userInfoDto.roles = roleService.extractGrantedAuthorities(principal.authorities).map { it.authority }
            return userInfoDto
        }

        val user = userByUsername(auth.name)
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

        if (user.avatar == null) return

        avatarStore.unsetContent(user.avatar)
        user.avatar = null

        userRepository.save(user)
    }

    fun registerOrUpdateUser(user: User): User {
        return userRepository.save(user)
    }

    fun registerOrUpdateUser(user: User, role: Roles): User {
        return registerOrUpdateUser(user, listOf(role))
    }

    fun registerOrUpdateUser(user: User, roles: List<Roles>): User {
        user.password?.let { user.password = passwordEncoder.encode(it) }
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
            managedBySso = user.oidcProviderId != null,
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