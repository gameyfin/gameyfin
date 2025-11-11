package org.gameyfin.app.core.security

import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EncryptionConverterTest {

    private lateinit var converter: EncryptionConverter

    @BeforeEach
    fun setup() {
        mockkObject(EncryptionUtils)
        converter = EncryptionConverter()
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
    fun `convertToDatabaseColumn should encrypt non-null attribute`() {
        val plainText = "sensitive data"
        val encrypted = "encrypted_data"

        every { EncryptionUtils.encrypt(plainText) } returns encrypted

        val result = converter.convertToDatabaseColumn(plainText)

        assertEquals(encrypted, result)
        verify(exactly = 1) { EncryptionUtils.encrypt(plainText) }
    }

    @Test
    fun `convertToDatabaseColumn should encrypt empty string`() {
        val plainText = ""
        val encrypted = "encrypted_empty"

        every { EncryptionUtils.encrypt(plainText) } returns encrypted

        val result = converter.convertToDatabaseColumn(plainText)

        assertEquals(encrypted, result)
        verify(exactly = 1) { EncryptionUtils.encrypt(plainText) }
    }

    @Test
    fun `convertToDatabaseColumn should encrypt string with special characters`() {
        val plainText = "special!@#$%^&*()_+-={}[]|\\:;\"'<>,.?/"
        val encrypted = "encrypted_special"

        every { EncryptionUtils.encrypt(plainText) } returns encrypted

        val result = converter.convertToDatabaseColumn(plainText)

        assertEquals(encrypted, result)
        verify(exactly = 1) { EncryptionUtils.encrypt(plainText) }
    }

    @Test
    fun `convertToDatabaseColumn should encrypt long string`() {
        val plainText = "a".repeat(1000)
        val encrypted = "encrypted_long"

        every { EncryptionUtils.encrypt(plainText) } returns encrypted

        val result = converter.convertToDatabaseColumn(plainText)

        assertEquals(encrypted, result)
        verify(exactly = 1) { EncryptionUtils.encrypt(plainText) }
    }

    @Test
    fun `convertToEntityAttribute should return null when dbData is null`() {
        val result = converter.convertToEntityAttribute(null)

        assertNull(result)
        verify(exactly = 0) { EncryptionUtils.decrypt(any()) }
    }

    @Test
    fun `convertToEntityAttribute should decrypt non-null dbData`() {
        val encrypted = "encrypted_data"
        val decrypted = "sensitive data"

        every { EncryptionUtils.decrypt(encrypted) } returns decrypted

        val result = converter.convertToEntityAttribute(encrypted)

        assertEquals(decrypted, result)
        verify(exactly = 1) { EncryptionUtils.decrypt(encrypted) }
    }

    @Test
    fun `convertToEntityAttribute should decrypt to empty string`() {
        val encrypted = "encrypted_empty"
        val decrypted = ""

        every { EncryptionUtils.decrypt(encrypted) } returns decrypted

        val result = converter.convertToEntityAttribute(encrypted)

        assertEquals(decrypted, result)
        verify(exactly = 1) { EncryptionUtils.decrypt(encrypted) }
    }

    @Test
    fun `convertToEntityAttribute should decrypt string with special characters`() {
        val encrypted = "encrypted_special"
        val decrypted = "special!@#$%^&*()_+-={}[]|\\:;\"'<>,.?/"

        every { EncryptionUtils.decrypt(encrypted) } returns decrypted

        val result = converter.convertToEntityAttribute(encrypted)

        assertEquals(decrypted, result)
        verify(exactly = 1) { EncryptionUtils.decrypt(encrypted) }
    }

    @Test
    fun `convertToEntityAttribute should decrypt long string`() {
        val encrypted = "encrypted_long"
        val decrypted = "a".repeat(1000)

        every { EncryptionUtils.decrypt(encrypted) } returns decrypted

        val result = converter.convertToEntityAttribute(encrypted)

        assertEquals(decrypted, result)
        verify(exactly = 1) { EncryptionUtils.decrypt(encrypted) }
    }

    @Test
    fun `round trip conversion should preserve data`() {
        val originalData = "test data"
        val encrypted = "encrypted_test_data"

        every { EncryptionUtils.encrypt(originalData) } returns encrypted
        every { EncryptionUtils.decrypt(encrypted) } returns originalData

        val encryptedResult = converter.convertToDatabaseColumn(originalData)
        val decryptedResult = converter.convertToEntityAttribute(encryptedResult)

        assertEquals(originalData, decryptedResult)
    }

    @Test
    fun `convertToDatabaseColumn should handle unicode characters`() {
        val plainText = "Hello 世界 🌍"
        val encrypted = "encrypted_unicode"

        every { EncryptionUtils.encrypt(plainText) } returns encrypted

        val result = converter.convertToDatabaseColumn(plainText)

        assertEquals(encrypted, result)
        verify(exactly = 1) { EncryptionUtils.encrypt(plainText) }
    }

    @Test
    fun `convertToEntityAttribute should handle unicode characters`() {
        val encrypted = "encrypted_unicode"
        val decrypted = "Hello 世界 🌍"

        every { EncryptionUtils.decrypt(encrypted) } returns decrypted

        val result = converter.convertToEntityAttribute(encrypted)

        assertEquals(decrypted, result)
        verify(exactly = 1) { EncryptionUtils.decrypt(encrypted) }
    }
}

