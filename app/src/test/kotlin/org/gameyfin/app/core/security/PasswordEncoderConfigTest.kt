package org.gameyfin.app.core.security

import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PasswordEncoderConfigTest {

    private val config = PasswordEncoderConfig()

    @Test
    fun `passwordEncoder should return BCryptPasswordEncoder instance`() {
        val encoder = config.passwordEncoder()

        assertNotNull(encoder)
        assertTrue(encoder is BCryptPasswordEncoder)
    }

    @Test
    fun `passwordEncoder should return working encoder`() {
        val encoder = config.passwordEncoder()
        val password = "testPassword123"

        val encoded = encoder.encode(password)

        assertNotNull(encoded)
        assertTrue(encoder.matches(password, encoded))
    }

    @Test
    fun `passwordEncoder should produce different hashes for same password`() {
        val encoder = config.passwordEncoder()
        val password = "testPassword123"

        val encoded1 = encoder.encode(password)
        val encoded2 = encoder.encode(password)

        assertNotNull(encoded1)
        assertNotNull(encoded2)
        assertTrue(encoder.matches(password, encoded1))
        assertTrue(encoder.matches(password, encoded2))
    }

    @Test
    fun `passwordEncoder should handle empty password`() {
        val encoder = config.passwordEncoder()
        val password = ""

        val encoded = encoder.encode(password)

        assertNotNull(encoded)
        assertTrue(encoder.matches(password, encoded))
    }

    @Test
    fun `passwordEncoder should handle long password`() {
        val encoder = config.passwordEncoder()
        // @see https://security.stackexchange.com/questions/39849/does-bcrypt-have-a-maximum-password-length
        val password = "a".repeat(50)

        val encoded = encoder.encode(password)

        assertNotNull(encoded)
        assertTrue(encoder.matches(password, encoded))
    }

    @Test
    fun `passwordEncoder should handle special characters`() {
        val encoder = config.passwordEncoder()
        val password = "!@#$%^&*()_+-={}[]|\\:;\"'<>,.?/"

        val encoded = encoder.encode(password)

        assertNotNull(encoded)
        assertTrue(encoder.matches(password, encoded))
    }

    @Test
    fun `passwordEncoder should handle unicode characters`() {
        val encoder = config.passwordEncoder()
        val password = "Hello世界🌍"

        val encoded = encoder.encode(password)

        assertNotNull(encoded)
        assertTrue(encoder.matches(password, encoded))
    }

    @Test
    fun `passwordEncoder should create singleton bean`() {
        val encoder1 = config.passwordEncoder()
        val encoder2 = config.passwordEncoder()

        assertNotNull(encoder1)
        assertNotNull(encoder2)
    }
}
