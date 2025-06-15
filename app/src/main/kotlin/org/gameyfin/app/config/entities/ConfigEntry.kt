package org.gameyfin.app.config.entities

import jakarta.persistence.*
import org.gameyfin.app.core.security.EncryptionConverter

@Entity
@Table(name = "app_config")
class ConfigEntry(
    @Id
    @Column(name = "`key`", unique = true)
    val key: String,

    @Column(name = "`value`")
    @Convert(converter = EncryptionConverter::class)
    var value: String
)