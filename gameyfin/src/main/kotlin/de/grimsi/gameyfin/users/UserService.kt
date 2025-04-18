package de.grimsi.gameyfin.users

import de.grimsi.gameyfin.config.ConfigProperties
import de.grimsi.gameyfin.config.ConfigService
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.core.Utils
import de.grimsi.gameyfin.core.events.*
import de.grimsi.gameyfin.games.entities.Image
import de.grimsi.gameyfin.media.ImageService
import de.grimsi.gameyfin.users.dto.UserInfoDto
import de.grimsi.gameyfin.users.dto.UserRegistrationDto
import de.grimsi.gameyfin.users.dto.UserUpdateDto
import de.grimsi.gameyfin.users.emailconfirmation.EmailConfirmationService
import de.grimsi.gameyfin.users.entities.User
import de.grimsi.gameyfin.users.enums.RoleAssignmentResult
import de.grimsi.gameyfin.users.persistence.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
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


@Service
class UserService(
    private val userRepository: UserRepository,
    private val imageService: ImageService,
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
        val user = getByUsernameNonNull(username)

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

    fun getByUsernameNonNull(username: String): User {
        return userRepository.findByUsername(username) ?: throw UsernameNotFoundException("Unknown user '$username'")
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

        val user = getByUsernameNonNull(auth.name)
        return toUserInfo(user)
    }

    fun getAvatar(username: String): Image? {
        val user = getByUsernameNonNull(username)
        return user.avatar
    }

    fun updateAvatar(username: String, newAvatar: Image) {
        val user = getByUsernameNonNull(username)
        user.avatar = newAvatar
        userRepository.save(user)
    }

    fun deleteAvatar(username: String) {
        val user = getByUsernameNonNull(username)

        if (user.avatar == null) return
        imageService.deleteFile(user.avatar!!)
        user.avatar = null

        userRepository.save(user)
    }

    fun hasAvatar(username: String): Boolean {
        val user = getByUsernameNonNull(username)
        return user.avatar != null && user.avatar!!.id != null
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
        val user = getByUsernameNonNull(username)

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
        val targetUser = getByUsernameNonNull(username)

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
        val targetUser = getByUsernameNonNull(targetUsername)
        return canManage(targetUser)
    }

    fun canManage(targetUser: User): Boolean {
        val currentUser = SecurityContextHolder.getContext().authentication
        val currentUserLevel = roleService.getHighestRoleFromAuthorities(currentUser.authorities).powerLevel
        val targetUserLevel = roleService.getHighestRole(targetUser.roles).powerLevel
        return currentUserLevel > targetUserLevel
    }

    fun setUserEnabled(username: String, enabled: Boolean) {
        val user = getByUsernameNonNull(username)
        user.enabled = enabled
        userRepository.save(user)
        eventPublisher.publishEvent(AccountStatusChangedEvent(this, user, Utils.getBaseUrl()))
    }

    fun deleteUser(username: String) {
        val user = getByUsernameNonNull(username)
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
            avatarId = user.avatar?.id,
            managedBySso = user.oidcProviderId != null,
            roles = user.roles
        )
    }

    private fun toAuthorities(roles: Collection<Role>): List<GrantedAuthority> {
        return roles.map { r -> SimpleGrantedAuthority(r.roleName) }
    }
}