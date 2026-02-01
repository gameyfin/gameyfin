package org.gameyfin.app.config

import io.mockk.*
import org.gameyfin.app.config.entities.ConfigEntry
import org.gameyfin.app.config.persistence.ConfigRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.logging.LogLevel
import org.springframework.data.repository.findByIdOrNull
import tools.jackson.databind.ObjectMapper
import java.io.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConfigServiceTest {

    private lateinit var configRepository: ConfigRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var configService: ConfigService

    @BeforeEach
    fun setup() {
        configRepository = mockk()
        objectMapper = ObjectMapper()
        configService = ConfigService(configRepository, objectMapper)
    }

    // ========== String Type Tests ==========

    @Test
    fun `get returns String value from database`() {
        val key = "logs.folder"
        val value = "./logs"
        val configEntry = ConfigEntry(key, objectMapper.writeValueAsString(value))

        every { configRepository.findByIdOrNull(key) } returns configEntry

        val result = configService.get(ConfigProperties.Logs.Folder)

        assertEquals(value, result)
    }

    @Test
    fun `get returns default String value when not in database`() {
        val key = "logs.folder"

        every { configRepository.findByIdOrNull(key) } returns null

        val result = configService.get(ConfigProperties.Logs.Folder)

        assertEquals("./logs", result)
    }

    @Test
    fun `set stores String value as JSON in database`() {
        val key = "logs.folder"
        val value = "/var/logs"
        val slot = slot<ConfigEntry>()

        every { configRepository.findByIdOrNull(key) } returns null
        every { configRepository.save(capture(slot)) } returns mockk()

        configService.set(ConfigProperties.Logs.Folder, value)

        verify { configRepository.save(any()) }
        assertEquals(key, slot.captured.key)
        assertEquals(objectMapper.writeValueAsString(value), slot.captured.value)
    }

    // ========== Boolean Type Tests ==========

    @Test
    fun `get returns Boolean value from database`() {
        val key = "security.allow-public-access"
        val value = true
        val configEntry = ConfigEntry(key, objectMapper.writeValueAsString(value))

        every { configRepository.findByIdOrNull(key) } returns configEntry

        val result = configService.get(ConfigProperties.Security.AllowPublicAccess)

        assertEquals(value, result)
    }

    @Test
    fun `set stores Boolean value as JSON in database`() {
        val key = "security.allow-public-access"
        val value = true
        val slot = slot<ConfigEntry>()

        every { configRepository.findByIdOrNull(key) } returns null
        every { configRepository.save(capture(slot)) } returns mockk()

        configService.set(ConfigProperties.Security.AllowPublicAccess, value)

        verify { configRepository.save(any()) }
        assertEquals(key, slot.captured.key)
        assertEquals(objectMapper.writeValueAsString(value), slot.captured.value)
    }

    // ========== Int Type Tests ==========

    @Test
    fun `get returns Int value from database`() {
        val key = "logs.max-history-days"
        val value = 30
        val configEntry = ConfigEntry(key, objectMapper.writeValueAsString(value))

        every { configRepository.findByIdOrNull(key) } returns configEntry

        val result = configService.get(ConfigProperties.Logs.MaxHistoryDays)

        assertEquals(value, result)
    }

    @Test
    fun `set stores Int value as JSON in database`() {
        val key = "logs.max-history-days"
        val value = 60
        val slot = slot<ConfigEntry>()

        every { configRepository.findByIdOrNull(key) } returns null
        every { configRepository.save(capture(slot)) } returns mockk()

        configService.set(ConfigProperties.Logs.MaxHistoryDays, value)

        verify { configRepository.save(any()) }
        assertEquals(key, slot.captured.key)
        assertEquals(objectMapper.writeValueAsString(value), slot.captured.value)
    }

    // ========== Enum Type Tests ==========

    @Test
    fun `get returns Enum value from database`() {
        val key = "logs.level.gameyfin"
        val value = LogLevel.INFO
        val configEntry = ConfigEntry(key, objectMapper.writeValueAsString(value))

        every { configRepository.findByIdOrNull(key) } returns configEntry

        val result = configService.get(ConfigProperties.Logs.Level.Gameyfin)

        assertEquals(value, result)
    }

    @Test
    fun `set stores Enum value as JSON in database`() {
        val key = "logs.level.gameyfin"
        val value = LogLevel.DEBUG
        val slot = slot<ConfigEntry>()

        every { configRepository.findByIdOrNull(key) } returns null
        every { configRepository.save(capture(slot)) } returns mockk()

        configService.set(ConfigProperties.Logs.Level.Gameyfin, value)

        verify { configRepository.save(any()) }
        assertEquals(key, slot.captured.key)
        assertEquals(objectMapper.writeValueAsString(value), slot.captured.value)
    }

    @Test
    fun `get returns default Enum value when not in database`() {
        val key = "logs.level.gameyfin"

        every { configRepository.findByIdOrNull(key) } returns null

        val result = configService.get(ConfigProperties.Logs.Level.Gameyfin)

        assertEquals(LogLevel.INFO, result)
    }

    // ========== Array Type Tests ==========

    @Test
    fun `get returns String Array value from database`() {
        val key = "sso.oidc.oauth-scopes"
        val value = arrayOf("openid", "profile", "email")
        val configEntry = ConfigEntry(key, objectMapper.writeValueAsString(value))

        every { configRepository.findByIdOrNull(key) } returns configEntry

        val result = configService.get(ConfigProperties.SSO.OIDC.OAuthScopes)

        assertNotNull(result)
        assertEquals(value.size, result.size)
        assertEquals(value.toList(), result.toList())
    }

    @Test
    fun `set stores String Array value as JSON in database`() {
        val key = "sso.oidc.oauth-scopes"
        val value = arrayOf("openid", "profile")
        val slot = slot<ConfigEntry>()

        every { configRepository.findByIdOrNull(key) } returns null
        every { configRepository.save(capture(slot)) } returns mockk()

        configService.set(ConfigProperties.SSO.OIDC.OAuthScopes, value)

        verify { configRepository.save(any()) }
        assertEquals(key, slot.captured.key)
        assertEquals(objectMapper.writeValueAsString(value), slot.captured.value)
    }

    @Test
    fun `get returns empty String Array from database`() {
        val key = "sso.oidc.oauth-scopes"
        val value = arrayOf<String>()
        val configEntry = ConfigEntry(key, objectMapper.writeValueAsString(value))

        every { configRepository.findByIdOrNull(key) } returns configEntry

        val result = configService.get(ConfigProperties.SSO.OIDC.OAuthScopes)

        assertNotNull(result)
        assertEquals(0, result.size)
    }

    // ========== Update Method Tests ==========

    @Test
    fun `set updates existing config entry`() {
        val key = "logs.folder"
        val oldValue = "./logs"
        val newValue = "/var/logs"
        val existingEntry = ConfigEntry(key, objectMapper.writeValueAsString(oldValue))
        val slot = slot<ConfigEntry>()

        every { configRepository.findByIdOrNull(key) } returns existingEntry
        every { configRepository.save(capture(slot)) } returns mockk()

        configService.set(ConfigProperties.Logs.Folder, newValue)

        verify { configRepository.save(any()) }
        assertEquals(key, slot.captured.key)
        assertEquals(objectMapper.writeValueAsString(newValue), slot.captured.value)
    }

    // ========== Delete Method Tests ==========

    @Test
    fun `delete removes config entry from database`() {
        val key = "logs.folder"

        every { configRepository.deleteById(key) } just Runs

        configService.delete(key)

        verify { configRepository.deleteById(key) }
    }

    @Test
    fun `delete with unknown key throws IllegalArgumentException`() {
        val key = "unknown.key"

        assertThrows<IllegalArgumentException> {
            configService.delete(key)
        }
    }

    // ========== Get by String Key Tests ==========

    @Test
    fun `get by string key returns value from database`() {
        val key = "logs.folder"
        val value = "./logs"
        val configEntry = ConfigEntry(key, objectMapper.writeValueAsString(value))

        every { configRepository.findByIdOrNull(key) } returns configEntry

        val result = configService.get(key)

        assertEquals(value, result)
    }

    @Test
    fun `get by string key with unknown key throws IllegalArgumentException`() {
        val key = "unknown.key"

        assertThrows<IllegalArgumentException> {
            configService.get(key)
        }
    }

    // ========== Null Value Tests ==========

    @Test
    fun `get returns null when value is not set and no default exists`() {
        val key = "messages.providers.email.host"

        every { configRepository.findByIdOrNull(key) } returns null

        val result = configService.get(ConfigProperties.Messages.Providers.Email.Host)

        assertNull(result)
    }

    // ========== Error Handling Tests ==========

    @Test
    fun `deserializeValue throws IllegalArgumentException for invalid JSON`() {
        val key = "logs.max-history-days"
        val invalidJson = "not-a-number"
        val configEntry = ConfigEntry(key, invalidJson)

        every { configRepository.findByIdOrNull(key) } returns configEntry

        assertThrows<IllegalArgumentException> {
            configService.get(ConfigProperties.Logs.MaxHistoryDays)
        }
    }

    // ========== GetAll Tests ==========

    @Test
    fun `getAll returns all config properties`() {
        // Mock all possible config entries
        every { configRepository.findByIdOrNull(any()) } returns null

        val result = configService.getAll()

        // Verify we get multiple entries
        assert(result.isNotEmpty())

        // Check that each entry has the required fields
        result.forEach { entry ->
            assertNotNull(entry.key)
            assertNotNull(entry.type)
            assertNotNull(entry.description)
        }
    }

    @Test
    fun `getAll returns values from database when available`() {
        val key = "logs.folder"
        val value = "/custom/logs"
        val configEntry = ConfigEntry(key, objectMapper.writeValueAsString(value))

        every { configRepository.findByIdOrNull(key) } returns configEntry
        every { configRepository.findByIdOrNull(not(key)) } returns null

        val result = configService.getAll()

        val logsEntry = result.find { it.key == key }
        assertNotNull(logsEntry)
        assertEquals(value, logsEntry.value)
    }

    // ========== Type-Safe Set Method Tests ==========

    @Test
    fun `set with ConfigProperty delegates to set with string key`() {
        val key = "logs.folder"
        val value = "/var/logs"
        val slot = slot<ConfigEntry>()

        every { configRepository.findByIdOrNull(key) } returns null
        every { configRepository.save(capture(slot)) } returns mockk()

        configService.set(ConfigProperties.Logs.Folder, value)

        verify { configRepository.save(any()) }
        assertEquals(key, slot.captured.key)
    }

    // ========== Complex Object Tests (Future-proofing) ==========

    class TestComplexObject() : Serializable {
        var name: String = ""
        var count: Int = 0
        var enabled: Boolean = false

        constructor(name: String, count: Int, enabled: Boolean) : this() {
            this.name = name
            this.count = count
            this.enabled = enabled
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TestComplexObject) return false
            return name == other.name && count == other.count && enabled == other.enabled
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + count
            result = 31 * result + enabled.hashCode()
            return result
        }
    }

    @Test
    fun `ObjectMapper can serialize complex objects`() {
        val complexObject = TestComplexObject("test", 42, true)
        val json = objectMapper.writeValueAsString(complexObject)

        assertNotNull(json)
        assert(json.contains("test"))
        assert(json.contains("42"))
        assert(json.contains("true"))
    }

    @Test
    fun `ObjectMapper can deserialize complex objects`() {
        val complexObject = TestComplexObject("test", 42, true)
        val json = objectMapper.writeValueAsString(complexObject)
        val deserialized = objectMapper.readValue(json, TestComplexObject::class.java)

        assertEquals(complexObject, deserialized)
    }

    // ========== Edge Cases ==========

    @Test
    fun `set handles special characters in string values`() {
        val key = "messages.providers.email.password"
        val value = "p@ssw0rd!#$%^&*()"
        val slot = slot<ConfigEntry>()

        every { configRepository.findByIdOrNull(key) } returns null
        every { configRepository.save(capture(slot)) } returns mockk()

        configService.set(ConfigProperties.Messages.Providers.Email.Password, value)

        verify { configRepository.save(any()) }

        // Deserialize the saved value to ensure it was properly serialized
        val deserializedValue = objectMapper.readValue(slot.captured.value, String::class.java)
        assertEquals(value, deserializedValue)
    }

    @Test
    fun `set handles empty string values`() {
        val key = "messages.providers.email.host"
        val value = ""
        val slot = slot<ConfigEntry>()

        every { configRepository.findByIdOrNull(key) } returns null
        every { configRepository.save(capture(slot)) } returns mockk()

        configService.set(ConfigProperties.Messages.Providers.Email.Host, value)

        verify { configRepository.save(any()) }
        assertEquals(objectMapper.writeValueAsString(value), slot.captured.value)
    }

    @Test
    fun `Array with special characters is properly serialized and deserialized`() {
        val value = arrayOf("test@example.com", "user+tag@domain.com", "special!chars#here")
        val json = objectMapper.writeValueAsString(value)
        val deserialized = objectMapper.readValue(json, Array<String>::class.java)

        assertEquals(value.toList(), deserialized.toList())
    }
}
