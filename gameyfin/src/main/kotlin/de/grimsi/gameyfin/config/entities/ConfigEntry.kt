package de.grimsi.gameyfin.config.entities

import de.grimsi.gameyfin.core.security.EncryptionConverter
import jakarta.persistence.*

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