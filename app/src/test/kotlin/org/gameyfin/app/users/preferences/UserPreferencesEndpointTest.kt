package org.gameyfin.app.users.preferences

import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserPreferencesEndpointTest {

    private lateinit var userPreferencesService: UserPreferencesService
    private lateinit var endpoint: UserPreferencesEndpoint

    @BeforeEach
    fun setup() {
        userPreferencesService = mockk()
        endpoint = UserPreferencesEndpoint(userPreferencesService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `get should return value`() {
        every { userPreferencesService.get("preferred-theme") } returns "dark"
        assertEquals("dark", endpoint.get("preferred-theme"))
    }

    @Test
    fun `get should return null when not set`() {
        every { userPreferencesService.get("preferred-theme") } returns null
        assertNull(endpoint.get("preferred-theme"))
    }

    @Test
    fun `set should delegate to service`() {
        every { userPreferencesService.set("preferred-theme", "light") } just Runs
        endpoint.set("preferred-theme", "light")
        verify { userPreferencesService.set("preferred-theme", "light") }
    }

    @Test
    fun `set should handle arbitrary value`() {
        every { userPreferencesService.set("preferred-download-method", "stream") } just Runs
        endpoint.set("preferred-download-method", "stream")
        verify { userPreferencesService.set("preferred-download-method", "stream") }
    }
}

