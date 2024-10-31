package de.grimsi.gameyfin.core.plugins.config

import de.grimsi.gameyfin.core.security.EncryptionConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import java.io.Serializable

@Entity
@Table(name = "plugin_config")
data class PluginConfigEntry(
    @NotNull
    @EmbeddedId
    val id: PluginConfigEntryKey,

    @NotNull
    @Column(name = "`value`")
    @Convert(converter = EncryptionConverter::class)
    val value: String
)

@Embeddable
data class PluginConfigEntryKey(
    @Column(name = "plugin_id")
    val pluginId: String,

    @Column(name = "`key`")
    val key: String
) : Serializable