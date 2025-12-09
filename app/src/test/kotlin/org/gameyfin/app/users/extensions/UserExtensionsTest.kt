package org.gameyfin.app.users.extensions

import org.gameyfin.app.core.Role
import org.gameyfin.app.media.Image
import org.gameyfin.app.media.ImageType
import org.gameyfin.app.users.dto.ExtendedUserInfoDto
import org.gameyfin.app.users.dto.UserInfoDto
import org.gameyfin.app.users.entities.User
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserExtensionsTest {

    @Test
    fun `toUserInfoDto should map fields correctly`() {
        val user = User(id = 10L, username = "alice", email = "a@example.com")
        val dto: UserInfoDto = user.toUserInfoDto()
        assertEquals(10L, dto.id)
        assertEquals("alice", dto.username)
        assertFalse(dto.hasAvatar)
        assertEquals(null, dto.avatarId)
    }

    @Test
    fun `toExtendedUserInfoDto should map extended fields`() {
        val user = User(
            id = 11L,
            username = "bob",
            email = "b@example.com",
            emailConfirmed = true,
            enabled = true,
            roles = listOf(Role.ADMIN)
        )
        val dto: ExtendedUserInfoDto = user.toExtendedUserInfoDto()
        assertEquals(11L, dto.id)
        assertEquals("bob", dto.username)
        assertEquals("b@example.com", dto.email)
        assertTrue(dto.emailConfirmed)
        assertTrue(dto.enabled)
        assertTrue(dto.roles.contains(Role.ADMIN))
    }

    @Test
    fun `collection of roles toAuthorities should convert role names`() {
        val authorities = listOf(Role.USER, Role.ADMIN).toAuthorities()
        assertEquals(2, authorities.size)
        assertTrue(authorities.any { it.authority == Role.Names.USER })
        assertTrue(authorities.any { it.authority == Role.Names.ADMIN })
    }

    @Test
    fun `toExtendedUserInfoDto should set managedBySso when oidcProviderId present`() {
        val user = User(
            id = 12L,
            username = "charlie",
            email = "c@example.com",
            oidcProviderId = "oidc",
            roles = listOf(Role.USER)
        )
        val dto = user.toExtendedUserInfoDto()
        assertTrue(dto.managedBySso)
    }

    @Test
    fun `toUserInfoDto should reflect avatar presence`() {
        val user = User(id = 13L, username = "dana", email = "d@example.com")
        user.avatar = Image(type = ImageType.AVATAR)
        val dto = user.toUserInfoDto()
        assertTrue(dto.hasAvatar)
    }

    @Test
    fun `toExtendedUserInfoDto should include avatar id when present`() {
        val user = User(id = 14L, username = "eve", email = "e@example.com")
        user.avatar = Image(id = 99L, type = ImageType.AVATAR)
        val dto = user.toExtendedUserInfoDto()
        assertEquals(99L, dto.avatarId)
    }

    @Test
    fun `toAuthorities should handle empty roles`() {
        val authorities = emptyList<Role>().toAuthorities()
        assertTrue(authorities.isEmpty())
    }

    @Test
    fun `toAuthorities should produce SimpleGrantedAuthority instances`() {
        val authorities = listOf(Role.USER).toAuthorities()
        assertTrue(authorities.first() is SimpleGrantedAuthority)
    }
}

