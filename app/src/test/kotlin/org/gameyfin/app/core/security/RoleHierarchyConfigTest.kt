package org.gameyfin.app.core.security

import org.junit.jupiter.api.Test
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.core.authority.SimpleGrantedAuthority
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RoleHierarchyConfigTest {

    private val config = RoleHierarchyConfig()

    @Test
    fun `roleHierarchy should return RoleHierarchy instance`() {
        val hierarchy = config.roleHierarchy()

        assertNotNull(hierarchy)
    }

    @Test
    fun `roleHierarchy should define SUPERADMIN greater than ADMIN`() {
        val hierarchy = config.roleHierarchy()
        val superadminAuth = SimpleGrantedAuthority("ROLE_SUPERADMIN")

        val reachableAuthorities = hierarchy.getReachableGrantedAuthorities(listOf(superadminAuth))

        assertTrue(reachableAuthorities.any { it.authority == "ROLE_SUPERADMIN" })
        assertTrue(reachableAuthorities.any { it.authority == "ROLE_ADMIN" })
    }

    @Test
    fun `roleHierarchy should define ADMIN greater than USER`() {
        val hierarchy = config.roleHierarchy()
        val adminAuth = SimpleGrantedAuthority("ROLE_ADMIN")

        val reachableAuthorities = hierarchy.getReachableGrantedAuthorities(listOf(adminAuth))

        assertTrue(reachableAuthorities.any { it.authority == "ROLE_ADMIN" })
        assertTrue(reachableAuthorities.any { it.authority == "ROLE_USER" })
    }

    @Test
    fun `roleHierarchy should define SUPERADMIN with all roles`() {
        val hierarchy = config.roleHierarchy()
        val superadminAuth = SimpleGrantedAuthority("ROLE_SUPERADMIN")

        val reachableAuthorities = hierarchy.getReachableGrantedAuthorities(listOf(superadminAuth))

        assertEquals(3, reachableAuthorities.size)
        assertTrue(reachableAuthorities.any { it.authority == "ROLE_SUPERADMIN" })
        assertTrue(reachableAuthorities.any { it.authority == "ROLE_ADMIN" })
        assertTrue(reachableAuthorities.any { it.authority == "ROLE_USER" })
    }

    @Test
    fun `roleHierarchy should define USER with only USER role`() {
        val hierarchy = config.roleHierarchy()
        val userAuth = SimpleGrantedAuthority("ROLE_USER")

        val reachableAuthorities = hierarchy.getReachableGrantedAuthorities(listOf(userAuth))

        assertEquals(1, reachableAuthorities.size)
        assertTrue(reachableAuthorities.any { it.authority == "ROLE_USER" })
    }

    @Test
    fun `roleHierarchy should handle multiple authorities`() {
        val hierarchy = config.roleHierarchy()
        val adminAuth = SimpleGrantedAuthority("ROLE_ADMIN")
        val customAuth = SimpleGrantedAuthority("CUSTOM_AUTHORITY")

        val reachableAuthorities = hierarchy.getReachableGrantedAuthorities(listOf(adminAuth, customAuth))

        assertTrue(reachableAuthorities.any { it.authority == "ROLE_ADMIN" })
        assertTrue(reachableAuthorities.any { it.authority == "ROLE_USER" })
        assertTrue(reachableAuthorities.any { it.authority == "CUSTOM_AUTHORITY" })
    }

    @Test
    fun `roleHierarchy should handle empty authorities list`() {
        val hierarchy = config.roleHierarchy()

        val reachableAuthorities = hierarchy.getReachableGrantedAuthorities(emptyList())

        assertEquals(0, reachableAuthorities.size)
    }

    @Test
    fun `roleHierarchy should preserve non-role authorities`() {
        val hierarchy = config.roleHierarchy()
        val customAuth = SimpleGrantedAuthority("CUSTOM_PERMISSION")

        val reachableAuthorities = hierarchy.getReachableGrantedAuthorities(listOf(customAuth))

        assertTrue(reachableAuthorities.any { it.authority == "CUSTOM_PERMISSION" })
    }

    @Test
    fun `roleHierarchy should use RoleHierarchyImpl`() {
        val hierarchy = config.roleHierarchy()

        assertTrue(hierarchy is RoleHierarchyImpl)
    }

    @Test
    fun `roleHierarchy should correctly order hierarchy levels`() {
        val hierarchy = config.roleHierarchy()

        val superadminAuthorities = hierarchy.getReachableGrantedAuthorities(
            listOf(SimpleGrantedAuthority("ROLE_SUPERADMIN"))
        )
        assertEquals(3, superadminAuthorities.size)

        val adminAuthorities = hierarchy.getReachableGrantedAuthorities(
            listOf(SimpleGrantedAuthority("ROLE_ADMIN"))
        )
        assertEquals(2, adminAuthorities.size)

        val userAuthorities = hierarchy.getReachableGrantedAuthorities(
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        assertEquals(1, userAuthorities.size)
    }

    @Test
    fun `roleHierarchy should handle case sensitivity`() {
        val hierarchy = config.roleHierarchy()
        val lowerCaseAuth = SimpleGrantedAuthority("role_admin")

        val reachableAuthorities = hierarchy.getReachableGrantedAuthorities(listOf(lowerCaseAuth))

        assertEquals(1, reachableAuthorities.size)
        assertTrue(reachableAuthorities.any { it.authority == "role_admin" })
    }
}

