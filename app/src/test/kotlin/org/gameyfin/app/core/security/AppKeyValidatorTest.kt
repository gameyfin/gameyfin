package org.gameyfin.app.core.security

import io.mockk.clearAllMocks
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppKeyValidatorTest {

    private lateinit var validator: AppKeyValidator

    @BeforeEach
    fun setup() {
        validator = AppKeyValidator()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "   "])
    fun `hasValidAppKey should return false when APP_KEY is null, blank, or whitespace`(appKey: String?) {
        val result = validator.hasValidAppKey(appKey)

        assertFalse(result)
    }

    @ParameterizedTest
    @ValueSource(ints = [8, 12, 20, 64])
    fun `hasValidAppKey should return false when key length is invalid`(keySize: Int) {
        val invalidKey = ByteArray(keySize) { 0 }
        val encodedKey = Base64.getEncoder().encodeToString(invalidKey)

        val result = validator.hasValidAppKey(encodedKey)

        assertFalse(result)
    }

    @ParameterizedTest
    @ValueSource(ints = [16, 24, 32])
    fun `hasValidAppKey should return true when valid AES key is provided`(keySize: Int) {
        val validKey = ByteArray(keySize) { it.toByte() }
        val encodedKey = Base64.getEncoder().encodeToString(validKey)

        val result = validator.hasValidAppKey(encodedKey)

        assertTrue(result)
    }
}


