package de.grimsi.gameyfin.config.entities

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
    @Lob
    @Column(name = "`value`")
    var value: String
)