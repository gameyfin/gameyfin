package de.grimsi.gameyfin.core.plugins.config

import de.grimsi.gameyfin.core.security.EncryptionConverter
import jakarta.persistence.*
import java.io.Serializable

@Entity
@Table(name = "plugin_config")
data class PluginConfigEntry(
    @EmbeddedId
    val id: PluginConfigEntryKey,

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