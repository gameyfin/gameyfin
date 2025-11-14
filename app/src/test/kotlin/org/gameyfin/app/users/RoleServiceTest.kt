package org.gameyfin.app.users

import io.mockk.*
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.Role
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.persistence.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoleServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var configService: ConfigService
    private lateinit var roleService: RoleService

    @BeforeEach
    fun setup() {
        userRepository = mockk()
        configService = mockk()
        roleService = RoleService(userRepository, configService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `getAllRoles should return all role entries`() {
        val result = roleService.getAllRoles()

        assertEquals(Role.entries, result)
        assertEquals(3, result.size)
        assertTrue(result.contains(Role.USER))
        assertTrue(result.contains(Role.ADMIN))
        assertTrue(result.contains(Role.SUPERADMIN))
    }

    @Test
    fun `getUserCountForRole should return count from repository`() {
        every { userRepository.countUserByRolesContains(Role.ADMIN) } returns 5

        val result = roleService.getUserCountForRole(Role.ADMIN)

        assertEquals(5, result)
        verify(exactly = 1) { userRepository.countUserByRolesContains(Role.ADMIN) }
    }

    @Test
    fun `getUserCountForRole should return zero when no users have role`() {
        every { userRepository.countUserByRolesContains(Role.SUPERADMIN) } returns 0

        val result = roleService.getUserCountForRole(Role.SUPERADMIN)

        assertEquals(0, result)
    }

    @Test
    fun `getHighestRole should return role with highest power level`() {
        val roles = listOf(Role.USER, Role.ADMIN, Role.SUPERADMIN)

        val result = roleService.getHighestRole(roles)

        assertEquals(Role.SUPERADMIN, result)
    }

    @Test
    fun `getHighestRole should return USER when collection is empty`() {
        val roles = emptyList<Role>()

        val result = roleService.getHighestRole(roles)

        assertEquals(Role.USER, result)
    }

    @Test
    fun `getHighestRole should return single role when only one exists`() {
        val roles = listOf(Role.ADMIN)

        val result = roleService.getHighestRole(roles)

        assertEquals(Role.ADMIN, result)
    }

    @Test
    fun `getHighestRoleFromAuthorities should extract and return highest role`() {
        val authorities = listOf(
            SimpleGrantedAuthority(Role.Names.USER),
            SimpleGrantedAuthority(Role.Names.ADMIN)
        )

        val result = roleService.getHighestRoleFromAuthorities(authorities)

        assertEquals(Role.ADMIN, result)
    }

    @Test
    fun `getHighestRoleFromAuthorities should handle empty authorities`() {
        val authorities = emptyList<SimpleGrantedAuthority>()

        val result = roleService.getHighestRoleFromAuthorities(authorities)

        assertEquals(Role.USER, result)
    }

    @Test
    fun `getHighestRoleFromAuthorities should ignore invalid authorities`() {
        val authorities = listOf(
            SimpleGrantedAuthority("INVALID_ROLE"),
            SimpleGrantedAuthority(Role.Names.USER)
        )

        val result = roleService.getHighestRoleFromAuthorities(authorities)

        assertEquals(Role.USER, result)
    }

    @Test
    fun `getRolesBelowUser should return roles with lower power level`() {
        val user = mockk<User> {
            every { roles } returns listOf(Role.ADMIN)
        }

        val result = roleService.getRolesBelowUser(user)

        assertEquals(1, result.size)
        assertTrue(result.contains(Role.USER))
    }

    @Test
    fun `getRolesBelowUser should return empty list when user has lowest role`() {
        val user = mockk<User> {
            every { roles } returns listOf(Role.USER)
        }

        val result = roleService.getRolesBelowUser(user)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getRolesBelowUser should return all lower roles for SUPERADMIN`() {
        val user = mockk<User> {
            every { roles } returns listOf(Role.SUPERADMIN)
        }

        val result = roleService.getRolesBelowUser(user)

        assertEquals(2, result.size)
        assertTrue(result.contains(Role.USER))
        assertTrue(result.contains(Role.ADMIN))
    }

    @Test
    fun `getRolesBelowAuth should return roles below authenticated user's role`() {
        val auth = mockk<Authentication> {
            every { authorities } returns listOf(SimpleGrantedAuthority(Role.Names.ADMIN))
        }

        val result = roleService.getRolesBelowAuth(auth)

        assertEquals(1, result.size)
        assertTrue(result.contains(Role.USER))
    }

    @Test
    fun `authoritiesToRoles should convert authorities to roles`() {
        val authorities = listOf(
            SimpleGrantedAuthority(Role.Names.USER),
            SimpleGrantedAuthority(Role.Names.ADMIN)
        )

        val result = roleService.authoritiesToRoles(authorities)

        assertEquals(2, result.size)
        assertTrue(result.contains(Role.USER))
        assertTrue(result.contains(Role.ADMIN))
    }

    @Test
    fun `authoritiesToRoles should filter out invalid authorities`() {
        val authorities = listOf(
            SimpleGrantedAuthority("INVALID"),
            SimpleGrantedAuthority(Role.Names.USER)
        )

        val result = roleService.authoritiesToRoles(authorities)

        assertEquals(1, result.size)
        assertEquals(Role.USER, result[0])
    }

    @Test
    fun `authoritiesToRoles should return empty list when no valid authorities`() {
        val authorities = listOf(
            SimpleGrantedAuthority("INVALID1"),
            SimpleGrantedAuthority("INVALID2")
        )

        val result = roleService.authoritiesToRoles(authorities)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `extractGrantedAuthorities should convert SSO roles to internal roles`() {
        val userInfo = OidcUserInfo.builder()
            .claim("roles", listOf("GAMEYFIN_USER", "GAMEYFIN_ADMIN"))
            .build()
        val idToken = OidcIdToken.withTokenValue("token")
            .issuer("issuer")
            .subject("subject")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        val oidcAuthority = OidcUserAuthority(idToken, userInfo)
        val authorities = listOf(oidcAuthority)

        every { configService.get(ConfigProperties.SSO.OIDC.RolesClaim) } returns "roles"

        val result = roleService.extractGrantedAuthorities(authorities)

        assertEquals(2, result.size)
        assertTrue(result.any { it.authority == Role.Names.USER })
        assertTrue(result.any { it.authority == Role.Names.ADMIN })
    }

    @Test
    fun `extractGrantedAuthorities should filter out non-GAMEYFIN roles`() {
        val userInfo = OidcUserInfo.builder()
            .claim("roles", listOf("GAMEYFIN_USER", "OTHER_ROLE", "SOME_OTHER_ROLE"))
            .build()
        val idToken = OidcIdToken.withTokenValue("token")
            .issuer("issuer")
            .subject("subject")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        val oidcAuthority = OidcUserAuthority(idToken, userInfo)
        val authorities = listOf(oidcAuthority)

        every { configService.get(ConfigProperties.SSO.OIDC.RolesClaim) } returns "roles"

        val result = roleService.extractGrantedAuthorities(authorities)

        assertEquals(1, result.size)
        assertEquals(Role.Names.USER, result.first().authority)
    }

    @Test
    fun `extractGrantedAuthorities should handle null roles claim`() {
        val userInfo = OidcUserInfo.builder()
            .claim("sub", "user123")
            .build()
        val idToken = OidcIdToken.withTokenValue("token")
            .issuer("issuer")
            .subject("subject")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        val oidcAuthority = OidcUserAuthority(idToken, userInfo)
        val authorities = listOf(oidcAuthority)

        every { configService.get(ConfigProperties.SSO.OIDC.RolesClaim) } returns "roles"

        val result = roleService.extractGrantedAuthorities(authorities)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `extractGrantedAuthorities should handle empty authorities collection`() {
        val authorities = emptyList<OidcUserAuthority>()

        every { configService.get(ConfigProperties.SSO.OIDC.RolesClaim) } returns "roles"

        val result = roleService.extractGrantedAuthorities(authorities)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `extractGrantedAuthorities should handle non-OIDC authorities`() {
        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))

        every { configService.get(ConfigProperties.SSO.OIDC.RolesClaim) } returns "roles"

        val result = roleService.extractGrantedAuthorities(authorities)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `extractGrantedAuthorities should handle multiple OIDC authorities`() {
        val userInfo1 = OidcUserInfo.builder()
            .claim("roles", listOf("GAMEYFIN_USER"))
            .build()
        val idToken1 = OidcIdToken.withTokenValue("token1")
            .issuer("issuer")
            .subject("subject1")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        val userInfo2 = OidcUserInfo.builder()
            .claim("roles", listOf("GAMEYFIN_ADMIN"))
            .build()
        val idToken2 = OidcIdToken.withTokenValue("token2")
            .issuer("issuer")
            .subject("subject2")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        val authorities = listOf(
            OidcUserAuthority(idToken1, userInfo1),
            OidcUserAuthority(idToken2, userInfo2)
        )

        every { configService.get(ConfigProperties.SSO.OIDC.RolesClaim) } returns "roles"

        val result = roleService.extractGrantedAuthorities(authorities)

        assertEquals(2, result.size)
    }

    @Test
    fun `extractGrantedAuthorities should deduplicate roles`() {
        val userInfo = OidcUserInfo.builder()
            .claim("roles", listOf("GAMEYFIN_USER", "GAMEYFIN_USER", "GAMEYFIN_ADMIN"))
            .build()
        val idToken = OidcIdToken.withTokenValue("token")
            .issuer("issuer")
            .subject("subject")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        val oidcAuthority = OidcUserAuthority(idToken, userInfo)
        val authorities = listOf(oidcAuthority)

        every { configService.get(ConfigProperties.SSO.OIDC.RolesClaim) } returns "roles"

        val result = roleService.extractGrantedAuthorities(authorities)

        assertEquals(2, result.size)
    }

    @Test
    fun `extractGrantedAuthorities should handle empty roles list`() {
        val userInfo = OidcUserInfo.builder()
            .claim("roles", emptyList<String>())
            .build()
        val idToken = OidcIdToken.withTokenValue("token")
            .issuer("issuer")
            .subject("subject")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        val oidcAuthority = OidcUserAuthority(idToken, userInfo)
        val authorities = listOf(oidcAuthority)

        every { configService.get(ConfigProperties.SSO.OIDC.RolesClaim) } returns "roles"

        val result = roleService.extractGrantedAuthorities(authorities)

        assertTrue(result.isEmpty())
    }
}

