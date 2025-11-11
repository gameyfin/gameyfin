package org.gameyfin.app.core.security

import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class EncryptionUtils {
    companion object {
        private const val ALGORITHM = "AES"
        private val SECRET_KEY: SecretKeySpec by lazy {
            val base64Key = getAppKey()
            val decodedKey = Base64.getDecoder().decode(base64Key)
            SecretKeySpec(decodedKey, ALGORITHM)
        }

        // Extracted for testability
        internal fun getAppKey(): String {
            return System.getenv("APP_KEY")
                ?: throw IllegalStateException("APP_KEY environment variable is not set or empty")
        }

        fun encrypt(value: String): String {
            val cipher = Cipher.getInstance(ALGORITHM).apply {
                init(Cipher.ENCRYPT_MODE, SECRET_KEY)
            }
            val encryptedBytes = cipher.doFinal(value.toByteArray())
            return Base64.getEncoder().encodeToString(encryptedBytes)
        }

        fun decrypt(value: String): String {
            val cipher = Cipher.getInstance(ALGORITHM).apply {
                init(Cipher.DECRYPT_MODE, SECRET_KEY)
            }
            val decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(value))
            return String(decryptedBytes)
        }
    }
}