package org.gameyfin.app.users

import io.mockk.*
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.users.dto.ExtendedUserInfoDto
import org.gameyfin.app.users.dto.UserUpdateDto
import org.gameyfin.app.users.enums.RoleAssignmentResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserEndpointTest {

    private lateinit var userService: UserService
    private lateinit var roleService: RoleService
    private lateinit var userEndpoint: UserEndpoint

    @BeforeEach
    fun setup() {
        userService = mockk()
        roleService = mockk()
        userEndpoint = UserEndpoint(userService, roleService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `getUserInfo should return user info when authenticated`() {
        val userInfo = ExtendedUserInfoDto(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            emailConfirmed = true,
            enabled = true,
            hasAvatar = false,
            avatarId = null,
            managedBySso = false,
            roles = listOf(Role.USER)
        )
        val auth = mockk<Authentication> {
            every { isAuthenticated } returns true
            every { principal } returns "testuser"
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userService.getUserInfo() } returns userInfo

        val result = userEndpoint.getUserInfo()

        assertEquals(userInfo, result)
        verify(exactly = 1) { userService.getUserInfo() }
    }

    @Test
    fun `getUserInfo should return null when not authenticated`() {
        val auth = mockk<Authentication> {
            every { isAuthenticated } returns false
            every { principal } returns "anonymousUser"
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userService.getUserInfo() } returns mockk<ExtendedUserInfoDto>()

        val result = userEndpoint.getUserInfo()

        assertNull(result)
        verify(exactly = 0) { userService.getUserInfo() }
    }

    @Test
    fun `getUserInfo should return null when principal is anonymousUser`() {
        val auth = mockk<Authentication> {
            every { isAuthenticated } returns true
            every { principal } returns "anonymousUser"
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userService.getUserInfo() } returns mockk<ExtendedUserInfoDto>()

        val result = userEndpoint.getUserInfo()

        assertNull(result)
    }

    @Test
    fun `getUserInfo should return null when no authentication`() {
        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns null

        val result = userEndpoint.getUserInfo()

        assertNull(result)
    }

    @Test
    fun `updateUser should update current user with provided changes`() {
        val updates = UserUpdateDto(username = "newusername", password = null, email = null)
        val auth = mockk<Authentication> {
            every { name } returns "testuser"
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userService.updateUser("testuser", updates) } just Runs

        userEndpoint.updateUser(updates)

        verify(exactly = 1) { userService.updateUser("testuser", updates) }
    }

    @Test
    fun `updateUser should throw exception when no authentication`() {
        val updates = UserUpdateDto(username = "newusername", password = null, email = null)

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns null

        assertThrows(IllegalStateException::class.java) {
            userEndpoint.updateUser(updates)
        }
    }

    @Test
    fun `existsByMail should return true when user exists`() {
        every { userService.existsByEmail("test@example.com") } returns true

        val result = userEndpoint.existsByMail("test@example.com")

        assertTrue(result)
        verify(exactly = 1) { userService.existsByEmail("test@example.com") }
    }

    @Test
    fun `existsByMail should return false when user does not exist`() {
        every { userService.existsByEmail("nonexistent@example.com") } returns false

        val result = userEndpoint.existsByMail("nonexistent@example.com")

        assertFalse(result)
    }

    @Test
    fun `getAllUsers should return all users`() {
        val users = listOf(
            ExtendedUserInfoDto(
                id = 1L,
                username = "user1",
                email = "user1@example.com",
                emailConfirmed = true,
                enabled = true,
                hasAvatar = false,
                avatarId = null,
                managedBySso = false,
                roles = listOf(Role.USER)
            ),
            ExtendedUserInfoDto(
                id = 2L,
                username = "user2",
                email = "user2@example.com",
                emailConfirmed = true,
                enabled = true,
                hasAvatar = false,
                avatarId = null,
                managedBySso = false,
                roles = listOf(Role.ADMIN)
            )
        )

        every { userService.getAllUsers() } returns users

        val result = userEndpoint.getAllUsers()

        assertEquals(users, result)
        verify(exactly = 1) { userService.getAllUsers() }
    }

    @Test
    fun `getAllUsers should return empty list when no users`() {
        every { userService.getAllUsers() } returns emptyList()

        val result = userEndpoint.getAllUsers()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `updateUserByName should update specific user`() {
        val updates = UserUpdateDto(username = null, password = "newpass", email = null)

        every { userService.updateUser("targetuser", updates) } just Runs

        userEndpoint.updateUserByName("targetuser", updates)

        verify(exactly = 1) { userService.updateUser("targetuser", updates) }
    }

    @Test
    fun `setUserEnabled should enable user`() {
        every { userService.setUserEnabled("testuser", true) } just Runs

        userEndpoint.setUserEnabled("testuser", true)

        verify(exactly = 1) { userService.setUserEnabled("testuser", true) }
    }

    @Test
    fun `setUserEnabled should disable user`() {
        every { userService.setUserEnabled("testuser", false) } just Runs

        userEndpoint.setUserEnabled("testuser", false)

        verify(exactly = 1) { userService.setUserEnabled("testuser", false) }
    }

    @Test
    fun `deleteUser should delete current user`() {
        val auth = mockk<Authentication> {
            every { name } returns "testuser"
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userService.deleteUser("testuser") } just Runs

        userEndpoint.deleteUser()

        verify(exactly = 1) { userService.deleteUser("testuser") }
    }

    @Test
    fun `deleteUser should throw exception when no authentication`() {
        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns null

        assertThrows(IllegalStateException::class.java) {
            userEndpoint.deleteUser()
        }
    }

    @Test
    fun `deleteUserByName should delete specific user`() {
        every { userService.deleteUser("targetuser") } just Runs

        userEndpoint.deleteUserByName("targetuser")

        verify(exactly = 1) { userService.deleteUser("targetuser") }
    }

    @Test
    fun `getAvailableRoles should return all role names`() {
        every { roleService.getAllRoles() } returns listOf(Role.USER, Role.ADMIN, Role.SUPERADMIN)

        val result = userEndpoint.getAvailableRoles()

        assertEquals(3, result.size)
        assertTrue(result.contains(Role.USER.roleName))
        assertTrue(result.contains(Role.ADMIN.roleName))
        assertTrue(result.contains(Role.SUPERADMIN.roleName))
    }

    @Test
    fun `getRolesBelow should return roles below current user`() {
        val auth = mockk<Authentication>()

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { roleService.getRolesBelowAuth(auth) } returns listOf(Role.USER)

        val result = userEndpoint.getRolesBelow()

        assertEquals(1, result.size)
        assertEquals(Role.USER.roleName, result[0])
    }

    @Test
    fun `getRolesBelow should throw exception when no authentication`() {
        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns null

        assertThrows(IllegalStateException::class.java) {
            userEndpoint.getRolesBelow()
        }
    }

    @Test
    fun `canCurrentUserManage should return true when user can manage`() {
        every { userService.canManage("targetuser") } returns true

        val result = userEndpoint.canCurrentUserManage("targetuser")

        assertTrue(result)
        verify(exactly = 1) { userService.canManage("targetuser") }
    }

    @Test
    fun `canCurrentUserManage should return false when user cannot manage`() {
        every { userService.canManage("targetuser") } returns false

        val result = userEndpoint.canCurrentUserManage("targetuser")

        assertFalse(result)
    }

    @Test
    fun `assignRoles should return success result`() {
        val roles = listOf("ROLE_USER", "ROLE_ADMIN")

        every { userService.assignRoles("testuser", roles) } returns RoleAssignmentResult.SUCCESS

        val result = userEndpoint.assignRoles("testuser", roles)

        assertEquals(RoleAssignmentResult.SUCCESS, result)
        verify(exactly = 1) { userService.assignRoles("testuser", roles) }
    }

    @Test
    fun `assignRoles should return NO_ROLES_PROVIDED when empty list`() {
        val roles = emptyList<String>()

        every { userService.assignRoles("testuser", roles) } returns RoleAssignmentResult.NO_ROLES_PROVIDED

        val result = userEndpoint.assignRoles("testuser", roles)

        assertEquals(RoleAssignmentResult.NO_ROLES_PROVIDED, result)
    }

    @Test
    fun `assignRoles should return TARGET_POWER_LEVEL_TOO_HIGH when target has higher role`() {
        val roles = listOf("ROLE_USER")

        every { userService.assignRoles("superadmin", roles) } returns RoleAssignmentResult.TARGET_POWER_LEVEL_TOO_HIGH

        val result = userEndpoint.assignRoles("superadmin", roles)

        assertEquals(RoleAssignmentResult.TARGET_POWER_LEVEL_TOO_HIGH, result)
    }

    @Test
    fun `assignRoles should return ASSIGNED_ROLE_POWER_LEVEL_TOO_HIGH when assigning higher role`() {
        val roles = listOf("ROLE_SUPERADMIN")

        every {
            userService.assignRoles(
                "testuser",
                roles
            )
        } returns RoleAssignmentResult.ASSIGNED_ROLE_POWER_LEVEL_TOO_HIGH

        val result = userEndpoint.assignRoles("testuser", roles)

        assertEquals(RoleAssignmentResult.ASSIGNED_ROLE_POWER_LEVEL_TOO_HIGH, result)
    }

    @Test
    fun `assignRoles should handle single role assignment`() {
        val roles = listOf("ROLE_USER")

        every { userService.assignRoles("testuser", roles) } returns RoleAssignmentResult.SUCCESS

        val result = userEndpoint.assignRoles("testuser", roles)

        assertEquals(RoleAssignmentResult.SUCCESS, result)
    }

    @Test
    fun `assignRoles should handle multiple roles assignment`() {
        val roles = listOf("ROLE_USER", "ROLE_ADMIN")

        every { userService.assignRoles("testuser", roles) } returns RoleAssignmentResult.SUCCESS

        val result = userEndpoint.assignRoles("testuser", roles)

        assertEquals(RoleAssignmentResult.SUCCESS, result)
        verify(exactly = 1) { userService.assignRoles("testuser", roles) }
    }
}

