package org.gameyfin.app.core.plugins.config

import jakarta.persistence.*
import org.gameyfin.app.core.security.EncryptionConverter
import java.io.Serializable

@Entity
@Table(name = "plugin_config")
data class PluginConfigEntry(
    @EmbeddedId
    val id: PluginConfigEntryKey,

    @Lob
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