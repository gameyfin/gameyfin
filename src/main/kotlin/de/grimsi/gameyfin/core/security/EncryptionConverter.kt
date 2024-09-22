package de.grimsi.gameyfin.core.security

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@Converter
class EncryptionConverter : AttributeConverter<String, String> {

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
    }

    override fun convertToDatabaseColumn(attribute: String?): String? {
        return attribute?.let {
            try {
                val cipher = Cipher.getInstance(ALGORITHM).apply {
                    init(Cipher.ENCRYPT_MODE, SECRET_KEY)
                }
                val encryptedBytes = cipher.doFinal(it.toByteArray())
                Base64.getEncoder().encodeToString(encryptedBytes)
            } catch (e: Exception) {
                throw RuntimeException("Error during encryption", e)
            }
        }
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        return dbData?.let {
            try {
                val cipher = Cipher.getInstance(ALGORITHM).apply {
                    init(Cipher.DECRYPT_MODE, SECRET_KEY)
                }
                val decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(it))
                String(decryptedBytes)
            } catch (e: Exception) {
                throw RuntimeException("Error during decryption", e)
            }
        }
    }
}
