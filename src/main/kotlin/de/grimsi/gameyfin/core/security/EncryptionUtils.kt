package de.grimsi.gameyfin.core.security

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class EncryptionUtils {
    companion object {
        private const val ALGORITHM = "AES"
        private val SECRET_KEY: SecretKeySpec

        init {
            val base64Key = System.getenv("APP_KEY")
                ?: throw IllegalStateException("APP_KEY environment variable is not set or empty")

            val decodedKey = Base64.getDecoder().decode(base64Key)

            // Ensure the key length is valid for AES (128, 192, or 256 bits)
            if (decodedKey.size !in listOf(16, 24, 32)) {
                throw IllegalArgumentException("Invalid AES key length. Key must be 128, 192, or 256 bits.")
            }

            SECRET_KEY = SecretKeySpec(decodedKey, ALGORITHM)
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