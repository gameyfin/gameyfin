package org.gameyfin.app.core.security

import io.mockk.*
import org.gameyfin.app.core.Role
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecurityUtilsTest {

    private lateinit var securityContext: SecurityContext

    @BeforeEach
    fun setup() {
        securityContext = mockk<SecurityContext>()
        mockkStatic(SecurityContextHolder::class)
        every { SecurityContextHolder.getContext() } returns securityContext
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `getCurrentAuth should return authentication when present`() {
        val authentication = mockk<Authentication>()
        every { securityContext.authentication } returns authentication

        val result = getCurrentAuth()

        assertEquals(authentication, result)
    }

    @Test
    fun `getCurrentAuth should return null when authentication is null`() {
        every { securityContext.authentication } returns null

        val result = getCurrentAuth()

        assertNull(result)
    }

    @Test
    fun `isCurrentUserAdmin should return true when user is ADMIN`() {
        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            listOf(SimpleGrantedAuthority(Role.Names.ADMIN))
        )
        every { securityContext.authentication } returns authentication

        val result = isCurrentUserAdmin()

        assertTrue(result)
    }

    @Test
    fun `isCurrentUserAdmin should return true when user is SUPERADMIN`() {
        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            listOf(SimpleGrantedAuthority(Role.Names.SUPERADMIN))
        )
        every { securityContext.authentication } returns authentication

        val result = isCurrentUserAdmin()

        assertTrue(result)
    }

    @Test
    fun `isCurrentUserAdmin should return false when user is USER`() {
        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            listOf(SimpleGrantedAuthority(Role.Names.USER))
        )
        every { securityContext.authentication } returns authentication

        val result = isCurrentUserAdmin()

        assertFalse(result)
    }

    @Test
    fun `isCurrentUserAdmin should return false when authentication is null`() {
        every { securityContext.authentication } returns null

        val result = isCurrentUserAdmin()

        assertFalse(result)
    }

    @Test
    fun `isCurrentUserAdmin should return false when user has no authorities`() {
        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            emptyList()
        )
        every { securityContext.authentication } returns authentication

        val result = isCurrentUserAdmin()

        assertFalse(result)
    }

    @Test
    fun `isCurrentUserAdmin should return false when authorities is null`() {
        val authentication = mockk<Authentication>()
        every { authentication.authorities } returns null
        every { securityContext.authentication } returns authentication

        val result = isCurrentUserAdmin()

        assertFalse(result)
    }

    @Test
    fun `isCurrentUserAdmin should return true when user has both ADMIN and USER roles`() {
        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            listOf(
                SimpleGrantedAuthority(Role.Names.ADMIN),
                SimpleGrantedAuthority(Role.Names.USER)
            )
        )
        every { securityContext.authentication } returns authentication

        val result = isCurrentUserAdmin()

        assertTrue(result)
    }

    @Test
    fun `Authentication isAdmin should return true for ADMIN authority`() {
        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            listOf(SimpleGrantedAuthority(Role.Names.ADMIN))
        )

        assertTrue(authentication.isAdmin())
    }

    @Test
    fun `Authentication isAdmin should return true for SUPERADMIN authority`() {
        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            listOf(SimpleGrantedAuthority(Role.Names.SUPERADMIN))
        )

        assertTrue(authentication.isAdmin())
    }

    @Test
    fun `Authentication isAdmin should return false for USER authority`() {
        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            listOf(SimpleGrantedAuthority(Role.Names.USER))
        )

        assertFalse(authentication.isAdmin())
    }

    @Test
    fun `Authentication isAdmin should return false when authorities is empty`() {
        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            emptyList()
        )

        assertFalse(authentication.isAdmin())
    }

    @Test
    fun `Authentication isAdmin should return false when authorities is null`() {
        val authentication = mockk<Authentication>()
        every { authentication.authorities } returns null

        assertFalse(authentication.isAdmin())
    }

    @Test
    fun `Authentication isAdmin should return true when user has SUPERADMIN among multiple roles`() {
        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            listOf(
                SimpleGrantedAuthority(Role.Names.USER),
                SimpleGrantedAuthority(Role.Names.ADMIN),
                SimpleGrantedAuthority(Role.Names.SUPERADMIN)
            )
        )

        assertTrue(authentication.isAdmin())
    }

    @Test
    fun `Authentication isAdmin should return true when user has only ADMIN among multiple custom roles`() {
        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            listOf(
                SimpleGrantedAuthority("CUSTOM_ROLE"),
                SimpleGrantedAuthority(Role.Names.ADMIN),
                SimpleGrantedAuthority("ANOTHER_CUSTOM_ROLE")
            )
        )

        assertTrue(authentication.isAdmin())
    }

    @Test
    fun `Authentication isAdmin should return false for custom authorities only`() {
        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            listOf(
                SimpleGrantedAuthority("CUSTOM_ROLE_1"),
                SimpleGrantedAuthority("CUSTOM_ROLE_2")
            )
        )

        assertFalse(authentication.isAdmin())
    }

    @Test
    fun `Authentication isAdmin should be case sensitive`() {
        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            listOf(SimpleGrantedAuthority("role_admin"))
        )

        assertFalse(authentication.isAdmin())
    }

    @Test
    fun `isCurrentUserAdmin should handle authentication with mixed case authorities`() {
        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            listOf(
                SimpleGrantedAuthority("ROLE_USER"),
                SimpleGrantedAuthority("role_admin")
            )
        )
        every { securityContext.authentication } returns authentication

        val result = isCurrentUserAdmin()

        assertFalse(result)
    }
}

