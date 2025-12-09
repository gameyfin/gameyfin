package org.gameyfin.app.users

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.Utils
import org.gameyfin.app.core.events.AccountStatusChangedEvent
import org.gameyfin.app.core.events.EmailNeedsConfirmationEvent
import org.gameyfin.app.core.events.RegistrationAttemptWithExistingEmailEvent
import org.gameyfin.app.core.events.UserRegistrationWaitingForApprovalEvent
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.media.Image
import org.gameyfin.app.media.ImageService
import org.gameyfin.app.users.dto.ExtendedUserInfoDto
import org.gameyfin.app.users.dto.UserRegistrationDto
import org.gameyfin.app.users.dto.UserUpdateDto
import org.gameyfin.app.users.emailconfirmation.EmailConfirmationService
import org.gameyfin.app.users.enums.RoleAssignmentResult
import org.gameyfin.app.users.extensions.toAuthorities
import org.gameyfin.app.users.extensions.toExtendedUserInfoDto
import org.gameyfin.app.users.persistence.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.core.userdetails.User
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

        if (user.oidcProviderId != null && user.password == null) {
            // If the user is an OIDC user, we return a UserDetails with no password
            return User(
                user.username,
                "", // OIDC users do not have a password
                user.enabled,
                true,
                true,
                true,
                user.roles.toAuthorities()
            )
        }

        return User(
            user.username,
            user.password,
            user.enabled,
            true,
            true,
            true,
            user.roles.toAuthorities()
        )
    }

    fun existsByUsername(username: String): Boolean = userRepository.existsByUsername(username)
    fun existsByEmail(email: String): Boolean = userRepository.existsByEmail(email)

    fun findByOidcProviderId(oidcProviderId: String): org.gameyfin.app.users.entities.User? =
        userRepository.findByOidcProviderId(oidcProviderId)

    fun getUserById(id: Long): ExtendedUserInfoDto? {
        val user = userRepository.findById(id)
        return if (user.isPresent) {
            user.get().toExtendedUserInfoDto()
        } else {
            null
        }
    }

    fun getAllUsers(): List<ExtendedUserInfoDto> {
        return userRepository.findAll().map { it.toExtendedUserInfoDto() }
    }

    fun getByEmail(email: String): org.gameyfin.app.users.entities.User? {
        return userRepository.findByEmail(email)
    }

    fun getByUsername(username: String): org.gameyfin.app.users.entities.User? {
        return userRepository.findByUsername(username)
    }

    fun getByUsernameNonNull(username: String): org.gameyfin.app.users.entities.User {
        return userRepository.findByUsername(username) ?: throw UsernameNotFoundException("Unknown user '$username'")
    }

    fun getUserInfo(): ExtendedUserInfoDto {
        val auth = getCurrentAuth() ?: throw IllegalStateException("No authentication found")
        val principal = auth.principal

        if (principal is OidcUser) {
            val oidcUser = org.gameyfin.app.users.entities.User(principal)
            val user = userRepository.findByOidcProviderId(oidcUser.oidcProviderId!!)
                ?: throw UsernameNotFoundException("Unknown OIDC user with provider ID '${oidcUser.oidcProviderId}'")
            val userInfoDto = user.toExtendedUserInfoDto()
            userInfoDto.roles = roleService.extractGrantedAuthorities(principal.authorities)
                .mapNotNull { Role.safeValueOf(it.authority) }
            return userInfoDto
        }

        val user = getByUsernameNonNull(auth.name)
        return user.toExtendedUserInfoDto()
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
        imageService.deleteImageIfUnused(user.avatar!!)
        user.avatar = null

        userRepository.save(user)
    }

    fun hasAvatar(username: String): Boolean {
        val user = getByUsernameNonNull(username)
        return user.avatar != null && user.avatar!!.id != null
    }

    fun registerOrUpdateUser(user: org.gameyfin.app.users.entities.User): org.gameyfin.app.users.entities.User {
        // OIDC users can have null passwords, so we only encode if a password is provided
        if (user.password != null) {
            user.password = passwordEncoder.encode(user.password)
        }

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
            eventPublisher.publishEvent(
                RegistrationAttemptWithExistingEmailEvent(
                    this,
                    it,
                    Utils.getBaseUrl()
                )
            )
            return
        }

        val adminNeedsToApprove = config.get(ConfigProperties.Users.SignUps.ConfirmationRequired) == true

        var user = org.gameyfin.app.users.entities.User(
            username = registration.username,
            password = passwordEncoder.encode(registration.password),
            email = registration.email,
            enabled = !adminNeedsToApprove,
            roles = listOf(Role.USER)
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

    fun registerUserFromInvitation(
        registration: UserRegistrationDto,
        email: String
    ): org.gameyfin.app.users.entities.User {
        val user = org.gameyfin.app.users.entities.User(
            username = registration.username,
            password = passwordEncoder.encode(registration.password),
            email = email,
            emailConfirmed = true,
            enabled = true,
            roles = listOf(Role.USER)
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

    fun updatePassword(user: org.gameyfin.app.users.entities.User, newPassword: String) {
        user.password = passwordEncoder.encode(newPassword)
        userRepository.save(user)
    }

    fun assignRoles(username: String, roleNames: List<String>): RoleAssignmentResult {
        if (roleNames.isEmpty()) {
            return RoleAssignmentResult.NO_ROLES_PROVIDED
        }

        val currentUser = getCurrentAuth() ?: throw IllegalStateException("No authentication found")
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

        targetUser.roles = newAssignedRoles
        userRepository.save(targetUser)
        return RoleAssignmentResult.SUCCESS
    }

    fun canManage(targetUsername: String): Boolean {
        val targetUser = getByUsernameNonNull(targetUsername)
        return canManage(targetUser)
    }

    fun canManage(targetUser: org.gameyfin.app.users.entities.User): Boolean {
        val currentUser = getCurrentAuth() ?: throw IllegalStateException("No authentication found")
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
    }
}