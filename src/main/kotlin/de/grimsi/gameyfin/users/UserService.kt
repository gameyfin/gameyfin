package de.grimsi.gameyfin.users

import de.grimsi.gameyfin.config.ConfigProperties
import de.grimsi.gameyfin.config.ConfigService
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.core.Utils
import de.grimsi.gameyfin.core.events.*
import de.grimsi.gameyfin.users.dto.UserInfoDto
import de.grimsi.gameyfin.users.dto.UserRegistrationDto
import de.grimsi.gameyfin.users.dto.UserUpdateDto
import de.grimsi.gameyfin.users.emailconfirmation.EmailConfirmationService
import de.grimsi.gameyfin.users.entities.Avatar
import de.grimsi.gameyfin.users.entities.User
import de.grimsi.gameyfin.users.enums.RoleAssignmentResult
import de.grimsi.gameyfin.users.persistence.AvatarContentStore
import de.grimsi.gameyfin.users.persistence.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
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
    private val avatarStore: AvatarContentStore,
    private val passwordEncoder: PasswordEncoder,
    private val roleService: RoleService,
    private val sessionService: SessionService,
    private val emailConfirmationService: EmailConfirmationService,
    private val config: ConfigService,
    private val eventPublisher: ApplicationEventPublisher
) : UserDetailsService {

    private val log = KotlinLogging.logger {}

    val selfRegistrationAllowed: Boolean
        get() = config.get(ConfigProperties.Users.SignUps.Allow) == true

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
    fun existsByEmail(email: String): Boolean = userRepository.existsByEmail(email)

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
            userInfoDto.roles = roleService.extractGrantedAuthorities(principal.authorities)
                .mapNotNull { Role.safeValueOf(it.authority) }
                .toSet()
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
        user.password = passwordEncoder.encode(user.password)
        return userRepository.save(user)
    }

    fun selfRegisterUser(registration: UserRegistrationDto) {
        if (!selfRegistrationAllowed) {
            throw IllegalStateException("Sign ups are not allowed")
        }

        if (existsByUsername(registration.username)) {
            throw IllegalStateException("User with username '${registration.username}' already exists")
        }

        userRepository.findByEmail(registration.email)?.let {
            eventPublisher.publishEvent(RegistrationAttemptWithExistingEmailEvent(this, it, Utils.getBaseUrl()))
            return
        }

        val adminNeedsToApprove = config.get(ConfigProperties.Users.SignUps.ConfirmationRequired) == true

        var user = User(
            username = registration.username,
            password = passwordEncoder.encode(registration.password),
            email = registration.email,
            enabled = !adminNeedsToApprove,
            roles = setOf(Role.USER)
        )

        user = userRepository.save(user)

        if (adminNeedsToApprove) {
            eventPublisher.publishEvent(UserRegistrationWaitingForApprovalEvent(this, user))
        } else {
            eventPublisher.publishEvent(AccountStatusChangedEvent(this, user, Utils.getBaseUrl()))
        }

        if (!user.emailConfirmed) {
            val token = emailConfirmationService.generate(user)
            eventPublisher.publishEvent(EmailNeedsConfirmationEvent(this, token, Utils.getBaseUrl()))
        }
    }

    fun registerUserFromInvitation(registration: UserRegistrationDto, email: String): User {
        val user = User(
            username = registration.username,
            password = passwordEncoder.encode(registration.password),
            email = email,
            emailConfirmed = true,
            enabled = true,
            roles = setOf(Role.USER)
        )

        if (existsByUsername(user.username)) {
            throw IllegalStateException("User with username '${user.username}' already exists")
        }

        return userRepository.save(user)
    }

    fun updateUser(username: String, updates: UserUpdateDto) {
        val user = userByUsername(username)

        updates.username?.let { user.username = it }

        updates.password?.let {
            user.password = passwordEncoder.encode(it)
            sessionService.logoutAllSessions()
        }

        updates.email?.let {
            user.email = it
            user.emailConfirmed = false
            val token = emailConfirmationService.generate(user)
            eventPublisher.publishEvent(EmailNeedsConfirmationEvent(this, token, Utils.getBaseUrl()))
        }

        userRepository.save(user)
    }

    fun updatePassword(user: User, newPassword: String) {
        user.password = passwordEncoder.encode(newPassword)
        userRepository.save(user)
    }

    fun assignRoles(username: String, roleNames: List<String>): RoleAssignmentResult {
        if (roleNames.isEmpty()) {
            return RoleAssignmentResult.NO_ROLES_PROVIDED
        }

        val currentUser = SecurityContextHolder.getContext().authentication
        val targetUser = userByUsername(username)

        if (!canManage(targetUser)) {
            log.error { "User ${currentUser.name} tried to assign roles to user with higher or equal power level to their own" }
            return RoleAssignmentResult.TARGET_POWER_LEVEL_TOO_HIGH
        }

        val newAssignedRoles = roleNames.mapNotNull { r -> Role.safeValueOf(r) }
        val newAssignedRolesLevel = roleService.getHighestRole(newAssignedRoles).powerLevel
        val currentUserLevel = roleService.getHighestRoleFromAuthorities(currentUser.authorities).powerLevel

        if (currentUserLevel <= newAssignedRolesLevel) {
            log.error { "User ${currentUser.name} tried to assign roles with higher or equal power level than their own" }
            return RoleAssignmentResult.ASSIGNED_ROLE_POWER_LEVEL_TOO_HIGH
        }

        targetUser.roles = newAssignedRoles.toMutableSet()
        userRepository.save(targetUser)
        return RoleAssignmentResult.SUCCESS
    }

    fun canManage(targetUsername: String): Boolean {
        val targetUser = userByUsername(targetUsername)
        return canManage(targetUser)
    }

    fun canManage(targetUser: User): Boolean {
        val currentUser = SecurityContextHolder.getContext().authentication
        val currentUserLevel = roleService.getHighestRoleFromAuthorities(currentUser.authorities).powerLevel
        val targetUserLevel = roleService.getHighestRole(targetUser.roles).powerLevel
        return currentUserLevel > targetUserLevel
    }

    fun setUserEnabled(username: String, enabled: Boolean) {
        val user = userByUsername(username)
        user.enabled = enabled
        userRepository.save(user)
        eventPublisher.publishEvent(AccountStatusChangedEvent(this, user, Utils.getBaseUrl()))
    }

    fun deleteUser(username: String) {
        val user = userByUsername(username)
        userRepository.delete(user)
        eventPublisher.publishEvent(AccountDeletedEvent(this, user, Utils.getBaseUrl()))
    }

    fun toUserInfo(user: User): UserInfoDto {
        return UserInfoDto(
            username = user.username,
            email = user.email,
            emailConfirmed = user.emailConfirmed,
            isEnabled = user.enabled,
            hasAvatar = user.avatar != null,
            managedBySso = user.oidcProviderId != null,
            roles = user.roles
        )
    }

    private fun toAuthorities(roles: Collection<Role>): List<GrantedAuthority> {
        return roles.map { r -> SimpleGrantedAuthority(r.roleName) }
    }

    private fun userByUsername(username: String): User {
        return userRepository.findByUsername(username) ?: throw UsernameNotFoundException("Unknown user '$username'")
    }
}