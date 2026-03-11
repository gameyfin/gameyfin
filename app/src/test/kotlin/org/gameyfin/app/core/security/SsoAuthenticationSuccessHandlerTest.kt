package org.gameyfin.app.core.security

import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.config.MatchUsersBy
import org.gameyfin.app.core.Role
import org.gameyfin.app.users.RoleService
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.entities.User
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import kotlin.test.assertEquals

class SsoAuthenticationSuccessHandlerTest {

    private lateinit var userService: UserService
    private lateinit var roleService: RoleService
    private lateinit var config: ConfigService
    private lateinit var roleHierarchy: RoleHierarchy
    private lateinit var handler: SsoAuthenticationSuccessHandler

    private lateinit var request: HttpServletRequest
    private lateinit var response: HttpServletResponse
    private lateinit var authentication: Authentication
    private lateinit var securityContext: SecurityContext

    @BeforeEach
    fun setup() {
        userService = mockk(relaxed = true)
        roleService = mockk(relaxed = true)
        config = mockk()
        roleHierarchy = mockk(relaxed = true)

        handler = SsoAuthenticationSuccessHandler(userService, roleService, config, roleHierarchy)

        request = mockk()
        response = mockk(relaxed = true)
        authentication = mockk()
        securityContext = mockk(relaxed = true)

        mockkStatic(SecurityContextHolder::class)
        every { SecurityContextHolder.getContext() } returns securityContext

        // Default: no continue parameter → redirect to "/"
        every { request.getParameter("continue") } returns null

        // Default role resolution
        every { roleService.extractGrantedAuthorities(any()) } returns emptyList()
        every { roleService.authoritiesToRoles(any()) } returns listOf(Role.USER)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    // ── helper to build a minimal OidcUser mock ───────────────────────────────

    private fun oidcUser(
        subject: String = "sub-1",
        preferredUsername: String? = "alice",
        email: String = "alice@example.com",
        extraClaims: Map<String, String?> = emptyMap()
    ): OidcUser = mockk {
        every { this@mockk.subject } returns subject
        every { getClaim<String>("preferred_username") } returns preferredUsername
        every { getClaim<String>("name") } returns null
        every { getClaim<String>("email") } returns email
        extraClaims.forEach { (key, value) -> every { getClaim<String>(key) } returns value }
        every { authorities } returns emptyList()
        every { this@mockk.email } returns email
    }

    // ── Username resolution: configured attribute ─────────────────────────────

    @Test
    fun `uses configured username attribute when claim is present`() {
        val oidcUser = mockk<OidcUser> {
            every { subject } returns "sub-1"
            every { getClaim<String>("upn") } returns "alice@corp"
            every { getClaim<String>("preferred_username") } returns "fallback"
            every { getClaim<String>("name") } returns null
            every { getClaim<String>("email") } returns "alice@example.com"
            every { authorities } returns emptyList()
            every { email } returns "alice@example.com"
        }

        every { config.get(ConfigProperties.SSO.OIDC.UsernameClaim) } returns "upn"
        every { config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy) } returns MatchUsersBy.username
        every { authentication.principal } returns oidcUser
        every { authentication.credentials } returns null

        every { userService.findByOidcProviderId("sub-1") } returns null
        every { userService.getByUsername("alice@corp") } returns null
        val savedSlot = slot<User>()
        every { userService.registerOrUpdateUser(capture(savedSlot)) } returns mockk(relaxed = true)

        handler.onAuthenticationSuccess(request, response, authentication)

        assertEquals("alice@corp", savedSlot.captured.username)
    }

    @Test
    fun `falls back to preferred_username when configured attribute is absent`() {
        val oidcUser = mockk<OidcUser> {
            every { subject } returns "sub-2"
            every { getClaim<String>("upn") } returns null
            every { getClaim<String>("preferred_username") } returns "alice"
            every { getClaim<String>("name") } returns null
            every { getClaim<String>("email") } returns "alice@example.com"
            every { authorities } returns emptyList()
            every { email } returns "alice@example.com"
        }

        every { config.get(ConfigProperties.SSO.OIDC.UsernameClaim) } returns "upn"
        every { config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy) } returns MatchUsersBy.username
        every { authentication.principal } returns oidcUser
        every { authentication.credentials } returns null

        every { userService.findByOidcProviderId("sub-2") } returns null
        every { userService.getByUsername("alice") } returns null
        val savedSlot = slot<User>()
        every { userService.registerOrUpdateUser(capture(savedSlot)) } returns mockk(relaxed = true)

        handler.onAuthenticationSuccess(request, response, authentication)

        assertEquals("alice", savedSlot.captured.username)
    }

    @Test
    fun `falls back to sub when all claims are absent`() {
        val oidcUser = mockk<OidcUser> {
            every { subject } returns "sub-99"
            every { getClaim<String>(any()) } returns null
            every { authorities } returns emptyList()
            every { email } returns "sub99@example.com"
        }

        every { config.get(ConfigProperties.SSO.OIDC.UsernameClaim) } returns "preferred_username"
        every { config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy) } returns MatchUsersBy.username
        every { authentication.principal } returns oidcUser
        every { authentication.credentials } returns null

        every { userService.findByOidcProviderId("sub-99") } returns null
        every { userService.getByUsername("sub-99") } returns null
        val savedSlot = slot<User>()
        every { userService.registerOrUpdateUser(capture(savedSlot)) } returns mockk(relaxed = true)

        handler.onAuthenticationSuccess(request, response, authentication)

        assertEquals("sub-99", savedSlot.captured.username)
    }

    @Test
    fun `uses preferred_username fallback when UsernameAttribute config is null`() {
        val oidcUser = oidcUser(preferredUsername = "alice")

        every { config.get(ConfigProperties.SSO.OIDC.UsernameClaim) } returns null
        every { config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy) } returns MatchUsersBy.username
        every { authentication.principal } returns oidcUser
        every { authentication.credentials } returns null

        every { userService.findByOidcProviderId("sub-1") } returns null
        every { userService.getByUsername("alice") } returns null
        val savedSlot = slot<User>()
        every { userService.registerOrUpdateUser(capture(savedSlot)) } returns mockk(relaxed = true)

        handler.onAuthenticationSuccess(request, response, authentication)

        assertEquals("alice", savedSlot.captured.username)
    }

    // ── New user registration ─────────────────────────────────────────────────

    @Test
    fun `registers new user when not found by provider ID or username`() {
        val oidcUser = oidcUser(subject = "sub-new", preferredUsername = "newuser")

        every { config.get(ConfigProperties.SSO.OIDC.UsernameClaim) } returns "preferred_username"
        every { config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy) } returns MatchUsersBy.username
        every { authentication.principal } returns oidcUser
        every { authentication.credentials } returns null

        every { userService.findByOidcProviderId("sub-new") } returns null
        every { userService.getByUsername("newuser") } returns null
        val savedSlot = slot<User>()
        every { userService.registerOrUpdateUser(capture(savedSlot)) } returns mockk(relaxed = true)

        handler.onAuthenticationSuccess(request, response, authentication)

        verify(exactly = 1) { userService.registerOrUpdateUser(any()) }
        assertEquals("newuser", savedSlot.captured.username)
        assertEquals("sub-new", savedSlot.captured.oidcProviderId)
    }

    @Test
    fun `registers new user when not found by provider ID or email`() {
        val oidcUser = oidcUser(subject = "sub-new2", preferredUsername = "newuser2", email = "new@example.com")

        every { config.get(ConfigProperties.SSO.OIDC.UsernameClaim) } returns "preferred_username"
        every { config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy) } returns MatchUsersBy.email
        every { authentication.principal } returns oidcUser
        every { authentication.credentials } returns null

        every { userService.findByOidcProviderId("sub-new2") } returns null
        every { userService.getByEmail("new@example.com") } returns null
        val savedSlot = slot<User>()
        every { userService.registerOrUpdateUser(capture(savedSlot)) } returns mockk(relaxed = true)

        handler.onAuthenticationSuccess(request, response, authentication)

        assertEquals("newuser2", savedSlot.captured.username)
    }

    // ── Existing user update ──────────────────────────────────────────────────

    @Test
    fun `updates existing user found by provider ID with resolved username`() {
        val oidcUser =
            oidcUser(subject = "sub-existing", preferredUsername = "updated-name", email = "updated@example.com")
        val existingUser = User(
            id = 1L,
            username = "old-name",
            email = "old@example.com",
            oidcProviderId = "sub-existing",
            enabled = true
        )

        every { config.get(ConfigProperties.SSO.OIDC.UsernameClaim) } returns "preferred_username"
        every { config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy) } returns MatchUsersBy.username
        every { authentication.principal } returns oidcUser
        every { authentication.credentials } returns null

        every { userService.findByOidcProviderId("sub-existing") } returns existingUser
        every { userService.registerOrUpdateUser(existingUser) } returns mockk(relaxed = true)

        handler.onAuthenticationSuccess(request, response, authentication)

        assertEquals("updated-name", existingUser.username)
        assertEquals("updated@example.com", existingUser.email)
        assertEquals(true, existingUser.emailConfirmed)
        assertEquals("sub-existing", existingUser.oidcProviderId)
    }

    @Test
    fun `links and updates existing user found by username match`() {
        val oidcUser = oidcUser(subject = "sub-link", preferredUsername = "existinguser")
        val existingUser = User(
            id = 2L,
            username = "existinguser",
            email = "existing@example.com",
            enabled = true
        )

        every { config.get(ConfigProperties.SSO.OIDC.UsernameClaim) } returns "preferred_username"
        every { config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy) } returns MatchUsersBy.username
        every { authentication.principal } returns oidcUser
        every { authentication.credentials } returns null

        every { userService.findByOidcProviderId("sub-link") } returns null
        every { userService.getByUsername("existinguser") } returns existingUser
        every { userService.registerOrUpdateUser(existingUser) } returns mockk(relaxed = true)

        handler.onAuthenticationSuccess(request, response, authentication)

        assertEquals("sub-link", existingUser.oidcProviderId)
        assertEquals("existinguser", existingUser.username)
    }

    @Test
    fun `links and updates existing user found by email match`() {
        val oidcUser =
            oidcUser(subject = "sub-email-link", preferredUsername = "renamed", email = "matched@example.com")
        val existingUser = User(
            id = 3L,
            username = "oldusername",
            email = "matched@example.com",
            enabled = true
        )

        every { config.get(ConfigProperties.SSO.OIDC.UsernameClaim) } returns "preferred_username"
        every { config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy) } returns MatchUsersBy.email
        every { authentication.principal } returns oidcUser
        every { authentication.credentials } returns null

        every { userService.findByOidcProviderId("sub-email-link") } returns null
        every { userService.getByEmail("matched@example.com") } returns existingUser
        every { userService.registerOrUpdateUser(existingUser) } returns mockk(relaxed = true)

        handler.onAuthenticationSuccess(request, response, authentication)

        // Username should be updated to the resolved username from SSO
        assertEquals("renamed", existingUser.username)
        assertEquals("sub-email-link", existingUser.oidcProviderId)
    }

    // ── Role assignment ───────────────────────────────────────────────────────

    @Test
    fun `assigns USER role when no roles extracted from SSO`() {
        val oidcUser = oidcUser()

        every { config.get(ConfigProperties.SSO.OIDC.UsernameClaim) } returns "preferred_username"
        every { config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy) } returns MatchUsersBy.username
        every { authentication.principal } returns oidcUser
        every { authentication.credentials } returns null

        every { userService.findByOidcProviderId(any()) } returns null
        every { userService.getByUsername(any()) } returns null
        every { roleService.extractGrantedAuthorities(any()) } returns emptyList()
        every { roleService.authoritiesToRoles(any()) } returns emptyList() // empty → should default to USER

        val savedSlot = slot<User>()
        every { userService.registerOrUpdateUser(capture(savedSlot)) } returns mockk(relaxed = true)

        handler.onAuthenticationSuccess(request, response, authentication)

        assertEquals(listOf(Role.USER), savedSlot.captured.roles)
    }

    @Test
    fun `assigns ADMIN role when SSO provides it`() {
        val oidcUser = oidcUser()

        every { config.get(ConfigProperties.SSO.OIDC.UsernameClaim) } returns "preferred_username"
        every { config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy) } returns MatchUsersBy.username
        every { authentication.principal } returns oidcUser
        every { authentication.credentials } returns null

        every { userService.findByOidcProviderId(any()) } returns null
        every { userService.getByUsername(any()) } returns null
        val adminAuthority = SimpleGrantedAuthority(Role.Names.ADMIN)
        every { roleService.extractGrantedAuthorities(any()) } returns listOf(adminAuthority)
        every { roleService.authoritiesToRoles(listOf(adminAuthority)) } returns listOf(Role.ADMIN)

        val savedSlot = slot<User>()
        every { userService.registerOrUpdateUser(capture(savedSlot)) } returns mockk(relaxed = true)

        handler.onAuthenticationSuccess(request, response, authentication)

        assertEquals(listOf(Role.ADMIN), savedSlot.captured.roles)
    }

    // ── Redirect behaviour ────────────────────────────────────────────────────

    @Test
    fun `redirects to root when no continue parameter`() {
        val oidcUser = oidcUser()

        every { config.get(ConfigProperties.SSO.OIDC.UsernameClaim) } returns "preferred_username"
        every { config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy) } returns MatchUsersBy.username
        every { authentication.principal } returns oidcUser
        every { authentication.credentials } returns null
        every { request.getParameter("continue") } returns null

        every { userService.findByOidcProviderId(any()) } returns null
        every { userService.getByUsername(any()) } returns null
        every { userService.registerOrUpdateUser(any()) } returns mockk(relaxed = true)

        handler.onAuthenticationSuccess(request, response, authentication)

        verify { response.sendRedirect("/") }
    }

    @Test
    fun `redirects to continue URL when it starts with slash`() {
        val oidcUser = oidcUser()

        every { config.get(ConfigProperties.SSO.OIDC.UsernameClaim) } returns "preferred_username"
        every { config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy) } returns MatchUsersBy.username
        every { authentication.principal } returns oidcUser
        every { authentication.credentials } returns null
        every { request.getParameter("continue") } returns "/collection/games"

        every { userService.findByOidcProviderId(any()) } returns null
        every { userService.getByUsername(any()) } returns null
        every { userService.registerOrUpdateUser(any()) } returns mockk(relaxed = true)

        handler.onAuthenticationSuccess(request, response, authentication)

        verify { response.sendRedirect("/collection/games") }
    }

    @Test
    fun `ignores continue URL that does not start with slash`() {
        val oidcUser = oidcUser()

        every { config.get(ConfigProperties.SSO.OIDC.UsernameClaim) } returns "preferred_username"
        every { config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy) } returns MatchUsersBy.username
        every { authentication.principal } returns oidcUser
        every { authentication.credentials } returns null
        every { request.getParameter("continue") } returns "https://evil.example.com"

        every { userService.findByOidcProviderId(any()) } returns null
        every { userService.getByUsername(any()) } returns null
        every { userService.registerOrUpdateUser(any()) } returns mockk(relaxed = true)

        handler.onAuthenticationSuccess(request, response, authentication)

        verify { response.sendRedirect("/") }
    }

    // ── Unknown MatchExistingUsersBy ──────────────────────────────────────────

    @Test
    fun `throws when MatchExistingUsersBy has unknown value`() {
        val oidcUser = oidcUser()

        every { config.get(ConfigProperties.SSO.OIDC.UsernameClaim) } returns "preferred_username"
        every { config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy) } returns null
        every { authentication.principal } returns oidcUser
        every { authentication.credentials } returns null

        every { userService.findByOidcProviderId(any()) } returns null

        assertThrows<IllegalStateException> {
            handler.onAuthenticationSuccess(request, response, authentication)
        }
    }
}

