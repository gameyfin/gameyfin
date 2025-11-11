package org.gameyfin.app.core.security

import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EncryptionMapConverterTest {

    private lateinit var converter: EncryptionMapConverter

    @BeforeEach
    fun setup() {
        mockkObject(EncryptionUtils)
        converter = EncryptionMapConverter()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `convertToDatabaseColumn should return null when attribute is null`() {
        val result = converter.convertToDatabaseColumn(null)

        assertNull(result)
        verify(exactly = 0) { EncryptionUtils.encrypt(any()) }
    }

    @Test
    fun `convertToDatabaseColumn should encrypt non-null map`() {
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val encrypted = "encrypted_map_data"

        every { EncryptionUtils.encrypt(any()) } returns encrypted

        val result = converter.convertToDatabaseColumn(map)

        assertEquals(encrypted, result)
        verify(exactly = 1) { EncryptionUtils.encrypt(any()) }
    }

    @Test
    fun `convertToDatabaseColumn should encrypt empty map`() {
        val map = emptyMap<String, String>()
        val encrypted = "encrypted_empty_map"

        every { EncryptionUtils.encrypt(any()) } returns encrypted

        val result = converter.convertToDatabaseColumn(map)

        assertEquals(encrypted, result)
        verify(exactly = 1) { EncryptionUtils.encrypt(any()) }
    }

    @Test
    fun `convertToDatabaseColumn should encrypt map with single entry`() {
        val map = mapOf("singleKey" to "singleValue")
        val encrypted = "encrypted_single_entry"

        every { EncryptionUtils.encrypt(any()) } returns encrypted

        val result = converter.convertToDatabaseColumn(map)

        assertEquals(encrypted, result)
        verify(exactly = 1) { EncryptionUtils.encrypt(any()) }
    }

    @Test
    fun `convertToDatabaseColumn should encrypt map with special characters in values`() {
        val map = mapOf("key" to "value with !@#$%^&*()")
        val encrypted = "encrypted_special"

        every { EncryptionUtils.encrypt(any()) } returns encrypted

        val result = converter.convertToDatabaseColumn(map)

        assertEquals(encrypted, result)
        verify(exactly = 1) { EncryptionUtils.encrypt(any()) }
    }

    @Test
    fun `convertToDatabaseColumn should encrypt map with special characters in keys`() {
        val map = mapOf("key-with-dashes" to "value", "key.with.dots" to "value2")
        val encrypted = "encrypted_special_keys"

        every { EncryptionUtils.encrypt(any()) } returns encrypted

        val result = converter.convertToDatabaseColumn(map)

        assertEquals(encrypted, result)
        verify(exactly = 1) { EncryptionUtils.encrypt(any()) }
    }

    @Test
    fun `convertToDatabaseColumn should encrypt map with unicode characters`() {
        val map = mapOf("greeting" to "Hello 世界", "emoji" to "🌍")
        val encrypted = "encrypted_unicode"

        every { EncryptionUtils.encrypt(any()) } returns encrypted

        val result = converter.convertToDatabaseColumn(map)

        assertEquals(encrypted, result)
        verify(exactly = 1) { EncryptionUtils.encrypt(any()) }
    }

    @Test
    fun `convertToDatabaseColumn should encrypt large map`() {
        val map = (1..100).associate { "key$it" to "value$it" }
        val encrypted = "encrypted_large_map"

        every { EncryptionUtils.encrypt(any()) } returns encrypted

        val result = converter.convertToDatabaseColumn(map)

        assertEquals(encrypted, result)
        verify(exactly = 1) { EncryptionUtils.encrypt(any()) }
    }

    @Test
    fun `convertToEntityAttribute should return null when dbData is null`() {
        val result = converter.convertToEntityAttribute(null)

        assertNull(result)
        verify(exactly = 0) { EncryptionUtils.decrypt(any()) }
    }

    @Test
    fun `convertToEntityAttribute should decrypt non-null dbData to map`() {
        val encrypted = "encrypted_map_data"
        val jsonString = """{"key1":"value1","key2":"value2"}"""

        every { EncryptionUtils.decrypt(encrypted) } returns jsonString

        val result = converter.convertToEntityAttribute(encrypted)

        assertNotNull(result)
        assertEquals(2, result.size)
        assertEquals("value1", result["key1"])
        assertEquals("value2", result["key2"])
        verify(exactly = 1) { EncryptionUtils.decrypt(encrypted) }
    }

    @Test
    fun `convertToEntityAttribute should decrypt to empty map`() {
        val encrypted = "encrypted_empty_map"
        val jsonString = "{}"

        every { EncryptionUtils.decrypt(encrypted) } returns jsonString

        val result = converter.convertToEntityAttribute(encrypted)

        assertNotNull(result)
        assertEquals(0, result.size)
        verify(exactly = 1) { EncryptionUtils.decrypt(encrypted) }
    }

    @Test
    fun `convertToEntityAttribute should decrypt map with single entry`() {
        val encrypted = "encrypted_single_entry"
        val jsonString = """{"singleKey":"singleValue"}"""

        every { EncryptionUtils.decrypt(encrypted) } returns jsonString

        val result = converter.convertToEntityAttribute(encrypted)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("singleValue", result["singleKey"])
        verify(exactly = 1) { EncryptionUtils.decrypt(encrypted) }
    }

    @Test
    fun `convertToEntityAttribute should decrypt map with special characters`() {
        val encrypted = "encrypted_special"
        val jsonString = """{"key":"value with !@#$%^&*()"}"""

        every { EncryptionUtils.decrypt(encrypted) } returns jsonString

        val result = converter.convertToEntityAttribute(encrypted)

        assertNotNull(result)
        assertEquals("value with !@#$%^&*()", result["key"])
        verify(exactly = 1) { EncryptionUtils.decrypt(encrypted) }
    }

    @Test
    fun `convertToEntityAttribute should decrypt map with unicode characters`() {
        val encrypted = "encrypted_unicode"
        val jsonString = """{"greeting":"Hello 世界","emoji":"🌍"}"""

        every { EncryptionUtils.decrypt(encrypted) } returns jsonString

        val result = converter.convertToEntityAttribute(encrypted)

        assertNotNull(result)
        assertEquals("Hello 世界", result["greeting"])
        assertEquals("🌍", result["emoji"])
        verify(exactly = 1) { EncryptionUtils.decrypt(encrypted) }
    }

    @Test
    fun `round trip conversion should preserve map data`() {
        val originalMap = mapOf("key1" to "value1", "key2" to "value2")
        val jsonString = """{"key1":"value1","key2":"value2"}"""
        val encrypted = "encrypted_data"

        every { EncryptionUtils.encrypt(any()) } returns encrypted
        every { EncryptionUtils.decrypt(encrypted) } returns jsonString

        val encryptedResult = converter.convertToDatabaseColumn(originalMap)
        val decryptedResult = converter.convertToEntityAttribute(encryptedResult)

        assertNotNull(decryptedResult)
        assertEquals(originalMap.size, decryptedResult.size)
        assertEquals("value1", decryptedResult["key1"])
        assertEquals("value2", decryptedResult["key2"])
    }

    @Test
    fun `convertToEntityAttribute should handle map with empty string values`() {
        val encrypted = "encrypted_empty_values"
        val jsonString = """{"key1":"","key2":"value2"}"""

        every { EncryptionUtils.decrypt(encrypted) } returns jsonString

        val result = converter.convertToEntityAttribute(encrypted)

        assertNotNull(result)
        assertEquals(2, result.size)
        assertEquals("", result["key1"])
        assertEquals("value2", result["key2"])
    }

    @Test
    fun `convertToEntityAttribute should handle map with nested quotes in values`() {
        val encrypted = "encrypted_quotes"
        val jsonString = """{"key":"value with \"quotes\""}"""

        every { EncryptionUtils.decrypt(encrypted) } returns jsonString

        val result = converter.convertToEntityAttribute(encrypted)

        assertNotNull(result)
        assertEquals("value with \"quotes\"", result["key"])
    }
}

