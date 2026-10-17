package org.gameyfin.app.config.entities

import jakarta.persistence.*
import org.gameyfin.app.core.security.EncryptionConverter
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@EntityListeners(ConfigEntryEntityListener::class)
@Table(name = "app_config")
class ConfigEntry(
    @Id
    @Column(name = "`key`", unique = true)
    val key: String,

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "`value`")
    @Convert(converter = EncryptionConverter::class)
    var value: String
)
