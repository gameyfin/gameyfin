package org.gameyfin.app.core.security

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.databind.ObjectMapper

@Converter
class EncryptionMapConverter : AttributeConverter<Map<String, String>, String> {
    companion object {
        private val objectMapper = ObjectMapper()
    }

    override fun convertToDatabaseColumn(attribute: Map<String, String>?): String? {
        return attribute?.let {
            val jsonString = objectMapper.writeValueAsString(it)
            EncryptionUtils.encrypt(jsonString)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun convertToEntityAttribute(dbData: String?): Map<String, String>? {
        return dbData?.let {
            val decryptedString = EncryptionUtils.decrypt(it)
            objectMapper.readValue(decryptedString, Map::class.java) as Map<String, String>?
        }
    }
}