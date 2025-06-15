package org.gameyfin.app.core.security

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class EncryptionConverter : AttributeConverter<String, String> {
    override fun convertToDatabaseColumn(attribute: String?): String? {
        return attribute?.let { EncryptionUtils.encrypt(it) }
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        return dbData?.let { EncryptionUtils.decrypt(it) }
    }
}
