package de.grimsi.gameyfin.config.entities

import de.grimsi.gameyfin.core.security.EncryptionConverter
import jakarta.persistence.*
import jakarta.validation.constraints.NotNull

@Entity
@Table(name = "app_config")
class ConfigEntry(
    @Id
    @NotNull
    @Column(name = "`key`", unique = true)
    val key: String,

    @NotNull
    @Column(name = "`value`")
    @Convert(converter = EncryptionConverter::class)
    var value: String
)