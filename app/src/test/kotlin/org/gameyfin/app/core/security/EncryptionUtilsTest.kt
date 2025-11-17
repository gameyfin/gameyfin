package org.gameyfin.app.core.security

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class EncryptionUtilsTest {

    private val validBase64Key = Base64.getEncoder().encodeToString(ByteArray(16) { it.toByte() })

    @BeforeEach
    fun setup() {
        mockkObject(EncryptionUtils)
        every { EncryptionUtils.getAppKey() } returns validBase64Key
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `encrypt should return non-null encrypted string`() {
        val plaintext = "test data"

        val encrypted = EncryptionUtils.encrypt(plaintext)

        assertNotNull(encrypted)
    }

    @Test
    fun `encrypt should return different output than input`() {
        val plaintext = "test data"

        val encrypted = EncryptionUtils.encrypt(plaintext)

        assertNotEquals(plaintext, encrypted)
    }

    @Test
    fun `encrypt should return base64 encoded string`() {
        val plaintext = "test data"

        val encrypted = EncryptionUtils.encrypt(plaintext)

        assertNotNull(Base64.getDecoder().decode(encrypted))
    }

    @Test
    fun `encrypt should produce different output for different inputs`() {
        val plaintext1 = "test data 1"
        val plaintext2 = "test data 2"

        val encrypted1 = EncryptionUtils.encrypt(plaintext1)
        val encrypted2 = EncryptionUtils.encrypt(plaintext2)

        assertNotEquals(encrypted1, encrypted2)
    }

    @Test
    fun `encrypt should handle empty string`() {
        val plaintext = ""

        val encrypted = EncryptionUtils.encrypt(plaintext)

        assertNotNull(encrypted)
    }

    @Test
    fun `encrypt should handle unicode characters`() {
        val plaintext = "Hello 世界 🌍"

        val encrypted = EncryptionUtils.encrypt(plaintext)

        assertNotNull(encrypted)
    }

    @Test
    fun `encrypt should handle special characters`() {
        val plaintext = "!@#$%^&*()_+-={}[]|\\:;\"'<>,.?/"

        val encrypted = EncryptionUtils.encrypt(plaintext)

        assertNotNull(encrypted)
    }

    @Test
    fun `encrypt should handle long strings`() {
        val plaintext = "a".repeat(1000)

        val encrypted = EncryptionUtils.encrypt(plaintext)

        assertNotNull(encrypted)
    }

    @Test
    fun `encrypt should handle newlines and whitespace`() {
        val plaintext = "line1\nline2\tline3  line4"

        val encrypted = EncryptionUtils.encrypt(plaintext)

        assertNotNull(encrypted)
    }

    @Test
    fun `decrypt should return original plaintext`() {
        val plaintext = "test data"

        val encrypted = EncryptionUtils.encrypt(plaintext)
        val decrypted = EncryptionUtils.decrypt(encrypted)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt should handle empty string`() {
        val plaintext = ""

        val encrypted = EncryptionUtils.encrypt(plaintext)
        val decrypted = EncryptionUtils.decrypt(encrypted)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt should handle unicode characters`() {
        val plaintext = "Hello 世界 🌍"

        val encrypted = EncryptionUtils.encrypt(plaintext)
        val decrypted = EncryptionUtils.decrypt(encrypted)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt should handle special characters`() {
        val plaintext = "!@#$%^&*()_+-={}[]|\\:;\"'<>,.?/"

        val encrypted = EncryptionUtils.encrypt(plaintext)
        val decrypted = EncryptionUtils.decrypt(encrypted)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt should handle long strings`() {
        val plaintext = "a".repeat(1000)

        val encrypted = EncryptionUtils.encrypt(plaintext)
        val decrypted = EncryptionUtils.decrypt(encrypted)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt should handle newlines and whitespace`() {
        val plaintext = "line1\nline2\tline3  line4"

        val encrypted = EncryptionUtils.encrypt(plaintext)
        val decrypted = EncryptionUtils.decrypt(encrypted)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt should produce consistent output for same input within session`() {
        val plaintext = "test data"

        val encrypted1 = EncryptionUtils.encrypt(plaintext)
        val encrypted2 = EncryptionUtils.encrypt(plaintext)

        assertEquals(EncryptionUtils.decrypt(encrypted1), EncryptionUtils.decrypt(encrypted2))
    }

    @Test
    fun `round trip encryption and decryption should preserve data`() {
        val testCases = listOf(
            "simple text",
            "",
            "123456789",
            "special!@#$%",
            "unicode: 世界🌍",
            "multi\nline\ntext",
            "a".repeat(500)
        )

        testCases.forEach { plaintext ->
            val encrypted = EncryptionUtils.encrypt(plaintext)
            val decrypted = EncryptionUtils.decrypt(encrypted)
            assertEquals(plaintext, decrypted, "Failed for: $plaintext")
        }
    }

    @Test
    fun `decrypt should handle json strings`() {
        val plaintext = """{"key":"value","number":123}"""

        val encrypted = EncryptionUtils.encrypt(plaintext)
        val decrypted = EncryptionUtils.decrypt(encrypted)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt should handle xml strings`() {
        val plaintext = """<root><item>value</item></root>"""

        val encrypted = EncryptionUtils.encrypt(plaintext)
        val decrypted = EncryptionUtils.decrypt(encrypted)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt and decrypt should work with password-like strings`() {
        val password = "MyS3cur3P@ssw0rd!"

        val encrypted = EncryptionUtils.encrypt(password)
        val decrypted = EncryptionUtils.decrypt(encrypted)

        assertEquals(password, decrypted)
        assertNotEquals(password, encrypted)
    }

    @Test
    fun `encrypt should work with various string lengths`() {
        listOf(1, 15, 16, 17, 31, 32, 33, 100, 256, 1024).forEach { length ->
            val plaintext = "x".repeat(length)
            val encrypted = EncryptionUtils.encrypt(plaintext)
            val decrypted = EncryptionUtils.decrypt(encrypted)
            assertEquals(plaintext, decrypted, "Failed for length: $length")
        }
    }
}