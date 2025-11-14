package org.gameyfin.app.core.security

import io.mockk.*
import org.gameyfin.app.users.RoleService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuthorityMapperConfigTest {

    private lateinit var roleService: RoleService
    private lateinit var config: AuthorityMapperConfig

    @BeforeEach
    fun setup() {
        roleService = mockk<RoleService>()
        config = AuthorityMapperConfig(roleService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `userAuthoritiesMapper should return GrantedAuthoritiesMapper`() {
        val mapper = config.userAuthoritiesMapper()

        assertNotNull(mapper)
    }

    @Test
    fun `userAuthoritiesMapper should map authorities using roleService`() {
        val inputAuthorities = setOf(
            SimpleGrantedAuthority("ROLE_USER"),
            SimpleGrantedAuthority("ROLE_ADMIN")
        )
        val expectedAuthorities = setOf(
            SimpleGrantedAuthority("ROLE_USER"),
            SimpleGrantedAuthority("ROLE_ADMIN"),
            SimpleGrantedAuthority("ROLE_SUPERADMIN")
        )

        every { roleService.extractGrantedAuthorities(inputAuthorities) } returns expectedAuthorities

        val mapper = config.userAuthoritiesMapper()
        val result = mapper.mapAuthorities(inputAuthorities)

        assertEquals(expectedAuthorities, result)
        verify(exactly = 1) { roleService.extractGrantedAuthorities(inputAuthorities) }
    }

    @Test
    fun `userAuthoritiesMapper should handle empty authorities`() {
        val inputAuthorities = emptySet<SimpleGrantedAuthority>()
        val expectedAuthorities = emptySet<SimpleGrantedAuthority>()

        every { roleService.extractGrantedAuthorities(inputAuthorities) } returns expectedAuthorities

        val mapper = config.userAuthoritiesMapper()
        val result = mapper.mapAuthorities(inputAuthorities)

        assertEquals(expectedAuthorities, result)
        verify(exactly = 1) { roleService.extractGrantedAuthorities(inputAuthorities) }
    }

    @Test
    fun `userAuthoritiesMapper should handle single authority`() {
        val inputAuthorities = setOf(SimpleGrantedAuthority("ROLE_USER"))
        val expectedAuthorities = setOf(SimpleGrantedAuthority("ROLE_USER"))

        every { roleService.extractGrantedAuthorities(inputAuthorities) } returns expectedAuthorities

        val mapper = config.userAuthoritiesMapper()
        val result = mapper.mapAuthorities(inputAuthorities)

        assertEquals(expectedAuthorities, result)
        verify(exactly = 1) { roleService.extractGrantedAuthorities(inputAuthorities) }
    }

    @Test
    fun `userAuthoritiesMapper should preserve authority types`() {
        val inputAuthorities = setOf(
            SimpleGrantedAuthority("CUSTOM_AUTHORITY")
        )
        val expectedAuthorities = setOf(
            SimpleGrantedAuthority("CUSTOM_AUTHORITY")
        )

        every { roleService.extractGrantedAuthorities(inputAuthorities) } returns expectedAuthorities

        val mapper = config.userAuthoritiesMapper()
        val result = mapper.mapAuthorities(inputAuthorities)

        assertEquals(expectedAuthorities, result)
    }
}

