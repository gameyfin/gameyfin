package org.gameyfin.app.config.entities

import jakarta.persistence.*
import org.gameyfin.app.core.security.EncryptionConverter

@Entity
@EntityListeners(ConfigEntryEntityListener::class)
@Table(name = "app_config")
class ConfigEntry(
    @Id
    @Column(name = "`key`", unique = true)
    val key: String,

    @Lob
    @Column(name = "`value`")
    @Convert(converter = EncryptionConverter::class)
    var value: String
)