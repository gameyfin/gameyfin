package org.gameyfin.app.users

import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.events.AccountStatusChangedEvent
import org.gameyfin.app.core.events.EmailNeedsConfirmationEvent
import org.gameyfin.app.core.events.RegistrationAttemptWithExistingEmailEvent
import org.gameyfin.app.core.events.UserRegistrationWaitingForApprovalEvent
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.games.entities.Image
import org.gameyfin.app.media.ImageService
import org.gameyfin.app.users.dto.ExtendedUserInfoDto
import org.gameyfin.app.users.dto.UserRegistrationDto
import org.gameyfin.app.users.dto.UserUpdateDto
import org.gameyfin.app.users.emailconfirmation.EmailConfirmationService
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.enums.RoleAssignmentResult
import org.gameyfin.app.users.extensions.toAuthorities
import org.gameyfin.app.users.extensions.toExtendedUserInfoDto
import org.gameyfin.app.users.persistence.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var imageService: ImageService
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var roleService: RoleService
    private lateinit var sessionService: SessionService
    private lateinit var emailConfirmationService: EmailConfirmationService
    private lateinit var config: ConfigService
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var userService: UserService

    @BeforeEach
    fun setup() {
        userRepository = mockk()
        imageService = mockk()
        passwordEncoder = mockk()
        roleService = mockk()
        sessionService = mockk()
        emailConfirmationService = mockk()
        config = mockk()
        eventPublisher = mockk()

        userService = UserService(
            userRepository,
            imageService,
            passwordEncoder,
            roleService,
            sessionService,
            emailConfirmationService,
            config,
            eventPublisher
        )

        every { eventPublisher.publishEvent(any()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    private fun mockRequestContext(baseUrl: String = "http://localhost") {
        val request = mockk<HttpServletRequest>(relaxed = true)
        val requestAttributes = mockk<ServletRequestAttributes>(relaxed = true)

        val scheme = baseUrl.substringBefore("://")
        val afterScheme = baseUrl.substringAfter("://")
        val serverName = if (afterScheme.contains(":")) {
            afterScheme.substringBefore(":")
        } else {
            afterScheme
        }
        val serverPort = if (afterScheme.contains(":")) {
            afterScheme.substringAfter(":").toIntOrNull() ?: 80
        } else {
            80
        }

        every { request.scheme } returns scheme
        every { request.serverName } returns serverName
        every { request.serverPort } returns serverPort
        every { requestAttributes.request } returns request

        mockkStatic(RequestContextHolder::class)
        every { RequestContextHolder.getRequestAttributes() } returns requestAttributes
    }

    @Test
    fun `loadUserByUsername should return UserDetails for standard user`() {
        val user = User(
            id = 1L,
            username = "testuser",
            password = "encodedPassword",
            email = "test@example.com",
            enabled = true,
            roles = listOf(Role.USER)
        )

        every { userRepository.findByUsername("testuser") } returns user
        every { roleService.authoritiesToRoles(any()) } returns user.roles
        mockkStatic("org.gameyfin.app.users.extensions.UserExtensionsKt")
        every { user.roles.toAuthorities() } returns listOf(SimpleGrantedAuthority(Role.Names.USER))

        val result = userService.loadUserByUsername("testuser")

        assertEquals("testuser", result.username)
        assertEquals("encodedPassword", result.password)
        assertTrue(result.isEnabled)
        assertEquals(1, result.authorities.size)
    }

    @Test
    fun `loadUserByUsername should return UserDetails with empty password for OIDC user`() {
        val user = User(
            id = 1L,
            username = "oidcuser",
            password = null,
            email = "oidc@example.com",
            oidcProviderId = "oidc123",
            enabled = true,
            roles = listOf(Role.USER)
        )

        every { userRepository.findByUsername("oidcuser") } returns user
        mockkStatic("org.gameyfin.app.users.extensions.UserExtensionsKt")
        every { user.roles.toAuthorities() } returns listOf(SimpleGrantedAuthority(Role.Names.USER))

        val result = userService.loadUserByUsername("oidcuser")

        assertEquals("oidcuser", result.username)
        assertEquals("", result.password)
        assertTrue(result.isEnabled)
    }

    @Test
    fun `loadUserByUsername should throw exception when user not found`() {
        every { userRepository.findByUsername("nonexistent") } returns null

        assertThrows(UsernameNotFoundException::class.java) {
            userService.loadUserByUsername("nonexistent")
        }
    }

    @Test
    fun `loadUserByUsername should return UserDetails with disabled account`() {
        val user = User(
            id = 1L,
            username = "disabled",
            password = "pass",
            email = "disabled@example.com",
            enabled = false,
            roles = listOf(Role.USER)
        )

        every { userRepository.findByUsername("disabled") } returns user
        mockkStatic("org.gameyfin.app.users.extensions.UserExtensionsKt")
        every { user.roles.toAuthorities() } returns listOf(SimpleGrantedAuthority(Role.Names.USER))

        val result = userService.loadUserByUsername("disabled")

        assertFalse(result.isEnabled)
    }

    @Test
    fun `existsByUsername should return true when user exists`() {
        every { userRepository.existsByUsername("existing") } returns true

        val result = userService.existsByUsername("existing")

        assertTrue(result)
    }

    @Test
    fun `existsByUsername should return false when user does not exist`() {
        every { userRepository.existsByUsername("nonexistent") } returns false

        val result = userService.existsByUsername("nonexistent")

        assertFalse(result)
    }

    @Test
    fun `existsByEmail should return true when email exists`() {
        every { userRepository.existsByEmail("test@example.com") } returns true

        val result = userService.existsByEmail("test@example.com")

        assertTrue(result)
    }

    @Test
    fun `existsByEmail should return false when email does not exist`() {
        every { userRepository.existsByEmail("nonexistent@example.com") } returns false

        val result = userService.existsByEmail("nonexistent@example.com")

        assertFalse(result)
    }

    @Test
    fun `findByOidcProviderId should return user when found`() {
        val user = mockk<User>()
        every { userRepository.findByOidcProviderId("oidc123") } returns user

        val result = userService.findByOidcProviderId("oidc123")

        assertEquals(user, result)
    }

    @Test
    fun `findByOidcProviderId should return null when not found`() {
        every { userRepository.findByOidcProviderId("nonexistent") } returns null

        val result = userService.findByOidcProviderId("nonexistent")

        assertNull(result)
    }

    @Test
    fun `getAllUsers should return all users as DTOs`() {
        val user1 = User(id = 1L, username = "user1", email = "user1@example.com", roles = listOf(Role.USER))
        val user2 = User(id = 2L, username = "user2", email = "user2@example.com", roles = listOf(Role.ADMIN))

        every { userRepository.findAll() } returns listOf(user1, user2)
        mockkStatic("org.gameyfin.app.users.extensions.UserExtensionsKt")
        every { user1.toExtendedUserInfoDto() } returns mockk(relaxed = true)
        every { user2.toExtendedUserInfoDto() } returns mockk(relaxed = true)

        val result = userService.getAllUsers()

        assertEquals(2, result.size)
        verify(exactly = 1) { userRepository.findAll() }
    }

    @Test
    fun `getAllUsers should return empty list when no users exist`() {
        every { userRepository.findAll() } returns emptyList()

        val result = userService.getAllUsers()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getByEmail should return user when found`() {
        val user = mockk<User>()
        every { userRepository.findByEmail("test@example.com") } returns user

        val result = userService.getByEmail("test@example.com")

        assertEquals(user, result)
    }

    @Test
    fun `getByEmail should return null when not found`() {
        every { userRepository.findByEmail("nonexistent@example.com") } returns null

        val result = userService.getByEmail("nonexistent@example.com")

        assertNull(result)
    }

    @Test
    fun `getByUsername should return user when found`() {
        val user = mockk<User>()
        every { userRepository.findByUsername("testuser") } returns user

        val result = userService.getByUsername("testuser")

        assertEquals(user, result)
    }

    @Test
    fun `getByUsername should return null when not found`() {
        every { userRepository.findByUsername("nonexistent") } returns null

        val result = userService.getByUsername("nonexistent")

        assertNull(result)
    }

    @Test
    fun `getByUsernameNonNull should return user when found`() {
        val user = mockk<User>()
        every { userRepository.findByUsername("testuser") } returns user

        val result = userService.getByUsernameNonNull("testuser")

        assertEquals(user, result)
    }

    @Test
    fun `getByUsernameNonNull should throw exception when not found`() {
        every { userRepository.findByUsername("nonexistent") } returns null

        assertThrows(UsernameNotFoundException::class.java) {
            userService.getByUsernameNonNull("nonexistent")
        }
    }

    @Test
    fun `getUserInfo should return user info for standard user`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            roles = listOf(Role.USER),
            enabled = true
        )
        val auth = mockk<Authentication> {
            every { name } returns "testuser"
            every { principal } returns mockk<Any>()
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userRepository.findByUsername("testuser") } returns user
        mockkStatic("org.gameyfin.app.users.extensions.UserExtensionsKt")
        every { user.toExtendedUserInfoDto() } returns ExtendedUserInfoDto(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = false,
            enabled = true,
            hasAvatar = false,
            avatarId = null,
            managedBySso = false,
            roles = listOf(Role.USER)
        )

        val result = userService.getUserInfo()

        assertEquals("testuser", result.username)
        assertEquals("test@example.com", result.email)
    }

    @Test
    fun `getUserInfo should handle OIDC user`() {
        val oidcUser = mockk<OidcUser> {
            every { subject } returns "oidcuser"
            every { authorities } returns listOf(SimpleGrantedAuthority(Role.Names.USER))
            every { preferredUsername } returns "oidcuser"
            every { email } returns "oidc@example.com"
        }
        val user = User(
            id = 1L,
            username = "oidcuser",
            email = "oidc@example.com",
            oidcProviderId = "oidcProviderId123",
            roles = listOf(Role.USER),
            enabled = true
        )
        val auth = mockk<Authentication> {
            every { name } returns "oidcuser"
            every { principal } returns oidcUser
        }
        val dto = ExtendedUserInfoDto(
            id = 1L,
            username = "oidcuser",
            email = "oidc@example.com",
            emailConfirmed = true,
            enabled = true,
            hasAvatar = false,
            avatarId = null,
            managedBySso = true,
            roles = listOf(Role.USER)
        )

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userRepository.findByOidcProviderId("oidcuser") } returns user
        every { roleService.extractGrantedAuthorities(oidcUser.authorities) } returns listOf(
            SimpleGrantedAuthority(Role.Names.USER)
        )
        mockkStatic("org.gameyfin.app.users.extensions.UserExtensionsKt")
        every { user.toExtendedUserInfoDto() } returns dto

        val result = userService.getUserInfo()

        assertEquals("oidcuser", result.username)
        assertTrue(result.managedBySso)
    }

    @Test
    fun `getUserInfo should throw exception when no authentication found`() {
        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns null

        assertThrows(IllegalStateException::class.java) {
            userService.getUserInfo()
        }
    }

    @Test
    fun `getAvatar should return user's avatar`() {
        val image = mockk<Image>()
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            avatar = image,
            roles = listOf(Role.USER)
        )

        every { userRepository.findByUsername("testuser") } returns user

        val result = userService.getAvatar("testuser")

        assertEquals(image, result)
    }

    @Test
    fun `getAvatar should return null when user has no avatar`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            avatar = null,
            roles = listOf(Role.USER)
        )

        every { userRepository.findByUsername("testuser") } returns user

        val result = userService.getAvatar("testuser")

        assertNull(result)
    }

    @Test
    fun `updateAvatar should set new avatar and save user`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            roles = listOf(Role.USER)
        )
        val newAvatar = mockk<Image>()

        every { userRepository.findByUsername("testuser") } returns user
        every { userRepository.save(user) } returns user

        userService.updateAvatar("testuser", newAvatar)

        assertEquals(newAvatar, user.avatar)
        verify(exactly = 1) { userRepository.save(user) }
    }

    @Test
    fun `deleteAvatar should delete image if unused and clear avatar`() {
        val image = mockk<Image>(relaxed = true)
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            avatar = image,
            roles = listOf(Role.USER)
        )

        every { userRepository.findByUsername("testuser") } returns user
        every { imageService.deleteImageIfUnused(image) } just Runs
        every { userRepository.save(user) } returns user

        userService.deleteAvatar("testuser")

        assertNull(user.avatar)
        verify(exactly = 1) { imageService.deleteImageIfUnused(image) }
        verify(exactly = 1) { userRepository.save(user) }
    }

    @Test
    fun `deleteAvatar should do nothing when user has no avatar`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            avatar = null,
            roles = listOf(Role.USER)
        )

        every { userRepository.findByUsername("testuser") } returns user

        userService.deleteAvatar("testuser")

        verify(exactly = 0) { imageService.deleteImageIfUnused(any()) }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `hasAvatar should return true when user has avatar with id`() {
        val image = mockk<Image> {
            every { id } returns 1L
        }
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            avatar = image,
            roles = listOf(Role.USER)
        )

        every { userRepository.findByUsername("testuser") } returns user

        val result = userService.hasAvatar("testuser")

        assertTrue(result)
    }

    @Test
    fun `hasAvatar should return false when user has no avatar`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            avatar = null,
            roles = listOf(Role.USER)
        )

        every { userRepository.findByUsername("testuser") } returns user

        val result = userService.hasAvatar("testuser")

        assertFalse(result)
    }

    @Test
    fun `hasAvatar should return false when avatar has no id`() {
        val image = mockk<Image> {
            every { id } returns null
        }
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            avatar = image,
            roles = listOf(Role.USER)
        )

        every { userRepository.findByUsername("testuser") } returns user

        val result = userService.hasAvatar("testuser")

        assertFalse(result)
    }

    @Test
    fun `registerOrUpdateUser should encode password and save user`() {
        val user = User(
            id = 1L,
            username = "testuser",
            password = "plainPassword",
            email = "test@example.com",
            roles = listOf(Role.USER)
        )

        every { passwordEncoder.encode("plainPassword") } returns "encodedPassword"
        every { userRepository.save(user) } returns user

        val result = userService.registerOrUpdateUser(user)

        assertEquals("encodedPassword", result.password)
        verify(exactly = 1) { passwordEncoder.encode("plainPassword") }
        verify(exactly = 1) { userRepository.save(user) }
    }

    @Test
    fun `registerOrUpdateUser should not encode null password for OIDC users`() {
        val user = User(
            id = 1L,
            username = "oidcuser",
            password = null,
            email = "oidc@example.com",
            oidcProviderId = "oidc123",
            roles = listOf(Role.USER)
        )

        every { userRepository.save(user) } returns user

        val result = userService.registerOrUpdateUser(user)

        assertNull(result.password)
        verify(exactly = 0) { passwordEncoder.encode(any()) }
        verify(exactly = 1) { userRepository.save(user) }
    }

    @Test
    fun `selfRegisterUser should create new user when sign ups allowed`() {
        val registration = UserRegistrationDto(
            username = "newuser",
            password = "password123",
            email = "new@example.com"
        )
        val user = User(
            id = 1L,
            username = "newuser",
            password = "encodedPassword",
            email = "new@example.com",
            enabled = true,
            roles = listOf(Role.USER)
        )

        every { config.get(ConfigProperties.Users.SignUps.Allow) } returns true
        every { config.get(ConfigProperties.Users.SignUps.ConfirmationRequired) } returns false
        every { userRepository.existsByUsername("newuser") } returns false
        every { userRepository.findByEmail("new@example.com") } returns null
        every { passwordEncoder.encode("password123") } returns "encodedPassword"
        every { userRepository.save(any()) } returns user
        every { emailConfirmationService.generate(user) } returns mockk()

        mockRequestContext()

        userService.selfRegisterUser(registration)

        verify(exactly = 1) { userRepository.save(any()) }
        verify(exactly = 1) { eventPublisher.publishEvent(ofType<AccountStatusChangedEvent>()) }
        verify(exactly = 1) { eventPublisher.publishEvent(ofType<EmailNeedsConfirmationEvent>()) }
    }

    @Test
    fun `selfRegisterUser should throw exception when sign ups not allowed`() {
        val registration = UserRegistrationDto(
            username = "newuser",
            password = "password123",
            email = "new@example.com"
        )

        every { config.get(ConfigProperties.Users.SignUps.Allow) } returns false

        assertThrows(IllegalStateException::class.java) {
            userService.selfRegisterUser(registration)
        }
    }

    @Test
    fun `selfRegisterUser should throw exception when username already exists`() {
        val registration = UserRegistrationDto(
            username = "existinguser",
            password = "password123",
            email = "new@example.com"
        )

        every { config.get(ConfigProperties.Users.SignUps.Allow) } returns true
        every { userRepository.existsByUsername("existinguser") } returns true

        assertThrows(IllegalStateException::class.java) {
            userService.selfRegisterUser(registration)
        }
    }

    @Test
    fun `selfRegisterUser should publish event when email already exists`() {
        val registration = UserRegistrationDto(
            username = "newuser",
            password = "password123",
            email = "existing@example.com"
        )
        val existingUser = mockk<User>()

        every { config.get(ConfigProperties.Users.SignUps.Allow) } returns true
        every { userRepository.existsByUsername("newuser") } returns false
        every { userRepository.findByEmail("existing@example.com") } returns existingUser

        mockRequestContext()

        userService.selfRegisterUser(registration)

        verify(exactly = 1) { eventPublisher.publishEvent(ofType<RegistrationAttemptWithExistingEmailEvent>()) }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `selfRegisterUser should create disabled user when approval required`() {
        val registration = UserRegistrationDto(
            username = "newuser",
            password = "password123",
            email = "new@example.com"
        )
        val savedUser = slot<User>()

        every { config.get(ConfigProperties.Users.SignUps.Allow) } returns true
        every { config.get(ConfigProperties.Users.SignUps.ConfirmationRequired) } returns true
        every { userRepository.existsByUsername("newuser") } returns false
        every { userRepository.findByEmail("new@example.com") } returns null
        every { passwordEncoder.encode("password123") } returns "encodedPassword"
        every { userRepository.save(capture(savedUser)) } answers { savedUser.captured }
        every { emailConfirmationService.generate(any()) } returns mockk()

        mockRequestContext()

        userService.selfRegisterUser(registration)

        assertFalse(savedUser.captured.enabled)
        verify(exactly = 1) { eventPublisher.publishEvent(ofType<UserRegistrationWaitingForApprovalEvent>()) }
    }

    @Test
    fun `registerUserFromInvitation should create enabled user with confirmed email`() {
        val registration = UserRegistrationDto(
            username = "inviteduser",
            password = "password123",
            email = "invited@example.com"
        )
        val user = User(
            id = 1L,
            username = "inviteduser",
            password = "encodedPassword",
            email = "invited@example.com",
            emailConfirmed = true,
            enabled = true,
            roles = listOf(Role.USER)
        )

        every { userRepository.existsByUsername("inviteduser") } returns false
        every { passwordEncoder.encode("password123") } returns "encodedPassword"
        every { userRepository.save(any()) } returns user

        val result = userService.registerUserFromInvitation(registration, "invited@example.com")

        assertTrue(result.emailConfirmed)
        assertTrue(result.enabled)
        verify(exactly = 1) { userRepository.save(any()) }
    }

    @Test
    fun `registerUserFromInvitation should throw exception when username exists`() {
        val registration = UserRegistrationDto(
            username = "existinguser",
            password = "password123",
            email = "new@example.com"
        )

        every { userRepository.existsByUsername("existinguser") } returns true
        every { passwordEncoder.encode("password123") } returns "encodedNewPassword"

        assertThrows(IllegalStateException::class.java) {
            userService.registerUserFromInvitation(registration, "new@example.com")
        }
    }

    @Test
    fun `updateUser should update username when provided`() {
        val user = User(
            id = 1L,
            username = "oldusername",
            email = "test@example.com",
            roles = listOf(Role.USER)
        )
        val updates = UserUpdateDto(username = "newusername", password = null, email = null)

        every { userRepository.findByUsername("oldusername") } returns user
        every { userRepository.save(user) } returns user

        userService.updateUser("oldusername", updates)

        assertEquals("newusername", user.username)
        verify(exactly = 1) { userRepository.save(user) }
    }

    @Test
    fun `updateUser should update password and logout all sessions when provided`() {
        val user = User(
            id = 1L,
            username = "testuser",
            password = "oldPassword",
            email = "test@example.com",
            roles = listOf(Role.USER)
        )
        val updates = UserUpdateDto(username = null, password = "newPassword", email = null)

        every { userRepository.findByUsername("testuser") } returns user
        every { passwordEncoder.encode("newPassword") } returns "encodedNewPassword"
        every { sessionService.logoutAllSessions() } just Runs
        every { userRepository.save(user) } returns user

        userService.updateUser("testuser", updates)

        assertEquals("encodedNewPassword", user.password)
        verify(exactly = 1) { sessionService.logoutAllSessions() }
        verify(exactly = 1) { userRepository.save(user) }
    }

    @Test
    fun `updateUser should update email and create confirmation token when provided`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "old@example.com",
            emailConfirmed = true,
            roles = listOf(Role.USER)
        )
        val updates = UserUpdateDto(username = null, password = null, email = "new@example.com")

        every { userRepository.findByUsername("testuser") } returns user
        every { userRepository.save(user) } returns user
        every { emailConfirmationService.generate(user) } returns mockk()

        mockRequestContext()

        userService.updateUser("testuser", updates)

        assertEquals("new@example.com", user.email)
        assertFalse(user.emailConfirmed)
        verify(exactly = 1) { emailConfirmationService.generate(user) }
        verify(exactly = 1) { eventPublisher.publishEvent(ofType<EmailNeedsConfirmationEvent>()) }
    }

    @Test
    fun `updatePassword should encode and save new password`() {
        val user = User(
            id = 1L,
            username = "testuser",
            password = "oldPassword",
            email = "test@example.com",
            roles = listOf(Role.USER)
        )

        every { passwordEncoder.encode("newPassword") } returns "encodedNewPassword"
        every { userRepository.save(user) } returns user

        userService.updatePassword(user, "newPassword")

        assertEquals("encodedNewPassword", user.password)
        verify(exactly = 1) { userRepository.save(user) }
    }

    @Test
    fun `assignRoles should return NO_ROLES_PROVIDED when roles list is empty`() {
        val result = userService.assignRoles("testuser", emptyList())

        assertEquals(RoleAssignmentResult.NO_ROLES_PROVIDED, result)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `assignRoles should return TARGET_POWER_LEVEL_TOO_HIGH when target user has higher role`() {
        val targetUser = User(
            id = 1L,
            username = "superadmin",
            email = "super@example.com",
            roles = listOf(Role.SUPERADMIN)
        )
        val auth = mockk<Authentication> {
            every { name } returns "testuser"
            every { principal } returns mockk<Any>()
            every { authorities } returns listOf(SimpleGrantedAuthority(Role.Names.ADMIN))
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userRepository.findByUsername("superadmin") } returns targetUser
        every { roleService.getHighestRole(listOf(Role.SUPERADMIN)) } returns Role.SUPERADMIN
        every { roleService.getHighestRoleFromAuthorities(listOf(SimpleGrantedAuthority(Role.Names.ADMIN))) } returns Role.ADMIN

        val result = userService.assignRoles("superadmin", listOf("ROLE_USER"))

        assertEquals(RoleAssignmentResult.TARGET_POWER_LEVEL_TOO_HIGH, result)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `assignRoles should return ASSIGNED_ROLE_POWER_LEVEL_TOO_HIGH when trying to assign higher role`() {
        val targetUser = User(
            id = 1L,
            username = "user",
            email = "user@example.com",
            roles = listOf(Role.USER)
        )
        val auth = mockk<Authentication> {
            every { name } returns "adminuser"
            every { principal } returns mockk<Any>()
            every { authorities } returns listOf(SimpleGrantedAuthority(Role.Names.ADMIN))
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userRepository.findByUsername("user") } returns targetUser
        every { roleService.getHighestRole(listOf(Role.USER)) } returns Role.USER
        every { roleService.getHighestRoleFromAuthorities(listOf(SimpleGrantedAuthority(Role.Names.ADMIN))) } returns Role.ADMIN
        mockkObject(Role.Companion)
        every { Role.safeValueOf("ROLE_SUPERADMIN") } returns Role.SUPERADMIN
        every { roleService.getHighestRole(listOf(Role.SUPERADMIN)) } returns Role.SUPERADMIN

        val result = userService.assignRoles("user", listOf("ROLE_SUPERADMIN"))

        assertEquals(RoleAssignmentResult.ASSIGNED_ROLE_POWER_LEVEL_TOO_HIGH, result)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `assignRoles should successfully assign valid roles`() {
        val targetUser = User(
            id = 1L,
            username = "user",
            email = "user@example.com",
            roles = listOf(Role.USER)
        )
        val auth = mockk<Authentication> {
            every { name } returns "adminuser"
            every { principal } returns mockk<Any>()
            every { authorities } returns listOf(SimpleGrantedAuthority(Role.Names.ADMIN))
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userRepository.findByUsername("user") } returns targetUser
        every { roleService.getHighestRole(listOf(Role.USER)) } returns Role.USER
        every { roleService.getHighestRoleFromAuthorities(listOf(SimpleGrantedAuthority(Role.Names.ADMIN))) } returns Role.ADMIN
        mockkObject(Role.Companion)
        every { Role.safeValueOf("ROLE_USER") } returns Role.USER
        every { roleService.getHighestRole(listOf(Role.USER)) } returns Role.USER
        every { userRepository.save(targetUser) } returns targetUser

        val result = userService.assignRoles("user", listOf("ROLE_USER"))

        assertEquals(RoleAssignmentResult.SUCCESS, result)
        verify(exactly = 1) { userRepository.save(targetUser) }
    }

    @Test
    fun `canManage should return true when current user has higher power level`() {
        val targetUser = User(
            id = 1L,
            username = "user",
            email = "user@example.com",
            roles = listOf(Role.USER)
        )
        val auth = mockk<Authentication> {
            every { name } returns "testuser"
            every { principal } returns mockk<Any>()
            every { authorities } returns listOf(SimpleGrantedAuthority(Role.Names.ADMIN))
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userRepository.findByUsername("user") } returns targetUser
        every { roleService.getHighestRoleFromAuthorities(listOf(SimpleGrantedAuthority(Role.Names.ADMIN))) } returns Role.ADMIN
        every { roleService.getHighestRole(listOf(Role.USER)) } returns Role.USER

        val result = userService.canManage("user")

        assertTrue(result)
    }

    @Test
    fun `canManage should return false when current user has equal or lower power level`() {
        val targetUser = User(
            id = 1L,
            username = "admin",
            email = "admin@example.com",
            roles = listOf(Role.ADMIN)
        )
        val auth = mockk<Authentication> {
            every { name } returns "testuser"
            every { principal } returns mockk<Any>()
            every { authorities } returns listOf(SimpleGrantedAuthority(Role.Names.USER))
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userRepository.findByUsername("admin") } returns targetUser
        every { roleService.getHighestRoleFromAuthorities(listOf(SimpleGrantedAuthority(Role.Names.USER))) } returns Role.USER
        every { roleService.getHighestRole(listOf(Role.ADMIN)) } returns Role.ADMIN

        val result = userService.canManage("admin")

        assertFalse(result)
    }

    @Test
    fun `setUserEnabled should enable user and publish event`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            enabled = false,
            roles = listOf(Role.USER)
        )

        every { userRepository.findByUsername("testuser") } returns user
        every { userRepository.save(user) } returns user

        mockRequestContext()

        userService.setUserEnabled("testuser", true)

        assertTrue(user.enabled)
        verify(exactly = 1) { userRepository.save(user) }
        verify(exactly = 1) { eventPublisher.publishEvent(ofType<AccountStatusChangedEvent>()) }
    }

    @Test
    fun `setUserEnabled should disable user and publish event`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            enabled = true,
            roles = listOf(Role.USER)
        )

        every { userRepository.findByUsername("testuser") } returns user
        every { userRepository.save(user) } returns user

        mockRequestContext()

        userService.setUserEnabled("testuser", false)

        assertFalse(user.enabled)
        verify(exactly = 1) { userRepository.save(user) }
    }

    @Test
    fun `deleteUser should delete user from repository`() {
        val user = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            roles = listOf(Role.USER)
        )

        every { userRepository.findByUsername("testuser") } returns user
        every { userRepository.delete(user) } just Runs

        userService.deleteUser("testuser")

        verify(exactly = 1) { userRepository.delete(user) }
    }

    @Test
    fun `selfRegistrationAllowed property should return config value`() {
        every { config.get(ConfigProperties.Users.SignUps.Allow) } returns true

        val result = userService.selfRegistrationAllowed

        assertTrue(result)
    }

    @Test
    fun `selfRegistrationAllowed property should handle null config value`() {
        every { config.get(ConfigProperties.Users.SignUps.Allow) } returns null

        val result = userService.selfRegistrationAllowed

        assertFalse(result)
    }
}

