package de.grimsi.gameyfin.config.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull

@Entity
@Table(name = "app_config")
class ConfigEntry(
    @Id
    @NotNull
    @Column(unique = true)
    val key: String,

    @NotNull
    var value: String
)