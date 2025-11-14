package org.gameyfin.app.users.preferences

import io.mockk.*
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.entities.User
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserPreferencesServiceTest {

    private lateinit var userPreferenceRepository: UserPreferenceRepository
    private lateinit var userService: UserService
    private lateinit var service: UserPreferencesService

    @BeforeEach
    fun setup() {
        userPreferenceRepository = mockk()
        userService = mockk()
        service = UserPreferencesService(userPreferenceRepository, userService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    private fun mockAuth(username: String, id: Long = 1L) {
        val user = User(id = id, username = username, email = "$username@example.com", enabled = true)
        val auth = mockk<org.springframework.security.core.Authentication> { every { name } returns username }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns auth
        every { userService.getByUsernameNonNull(username) } returns user
    }

    @Test
    fun `get type-safe should return null when preference not set`() {
        mockAuth("alice")
        every { userPreferenceRepository.findById(any<UserPreferenceKey>()) } returns Optional.empty()
        val result = service.get(UserPreferences.PreferredTheme)
        assertNull(result)
    }

    @Test
    fun `get type-safe should return stored value`() {
        mockAuth("bob")
        val key = UserPreferenceKey(UserPreferences.PreferredTheme.key, 1L)
        val preference = UserPreference(key, "dark")
        every { userPreferenceRepository.findById(key) } returns Optional.of(preference)
        val result = service.get(UserPreferences.PreferredTheme)
        assertEquals("dark", result)
    }

    @Test
    fun `get unsafe should return value as string`() {
        mockAuth("bob")
        val key = UserPreferenceKey(UserPreferences.PreferredTheme.key, 1L)
        val preference = UserPreference(key, "light")
        every { userPreferenceRepository.findById(key) } returns Optional.of(preference)
        val result = service.get(UserPreferences.PreferredTheme.key)
        assertEquals("light", result)
    }

    @Test
    fun `get unsafe should return null when missing`() {
        mockAuth("bob")
        every { userPreferenceRepository.findById(any<UserPreferenceKey>()) } returns Optional.empty()
        val result = service.get(UserPreferences.PreferredTheme.key)
        assertNull(result)
    }

    @Test
    fun `set type-safe should create new preference`() {
        mockAuth("charlie", id = 5L)
        every { userPreferenceRepository.findById(any<UserPreferenceKey>()) } returns Optional.empty()
        every { userPreferenceRepository.save(any()) } answers { firstArg() }
        service.set(UserPreferences.PreferredTheme, "retro")
        verify { userPreferenceRepository.save(match { it.value == "retro" }) }
    }

    @Test
    fun `set should update existing preference`() {
        mockAuth("dan")
        val key = UserPreferenceKey(UserPreferences.PreferredTheme.key, 1L)
        val existing = UserPreference(key, "old")
        every { userPreferenceRepository.findById(key) } returns Optional.of(existing)
        every { userPreferenceRepository.save(existing) } returns existing
        service.set(UserPreferences.PreferredTheme.key, "new")
        assertEquals("new", existing.value)
    }

    @Test
    fun `set should cast boolean preference`() {
        mockAuth("ed")
        val key = UserPreferenceKey(UserPreferences.PreferredDownloadMethod.key, 1L)
        every { userPreferenceRepository.findById(key) } returns Optional.empty()
        every { userPreferenceRepository.save(any()) } answers { firstArg() }
        service.set(UserPreferences.PreferredDownloadMethod.key, "direct")
        verify { userPreferenceRepository.save(any()) }
    }

    @Test
    fun `findUserPreference should throw for unknown key`() {
        mockAuth("gina")
        assertThrows(IllegalArgumentException::class.java) {
            service.get("does-not-exist")
        }
    }

    @Test
    fun `id should throw when no authentication`() {
        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns null
        assertThrows(IllegalStateException::class.java) {
            service.get(UserPreferences.PreferredTheme.key)
        }
    }

    @Test
    fun `set should warn and not throw when save fails`() {
        mockAuth("harry")
        every { userPreferenceRepository.findById(any<UserPreferenceKey>()) } returns Optional.empty()
        every { userPreferenceRepository.save(any()) } throws RuntimeException("db down")
        service.set(UserPreferences.PreferredTheme.key, "value")
        verify { userPreferenceRepository.save(any()) }
    }
}

