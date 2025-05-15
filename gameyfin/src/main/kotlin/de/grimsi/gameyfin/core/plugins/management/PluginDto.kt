package de.grimsi.gameyfin.core.plugins.management

import org.pf4j.PluginState

data class PluginDto(
    val id: String,
    val name: String,
    val description: String,
    val shortDescription: String? = null,
    val version: String,
    val author: String,
    val license: String? = null,
    val url: String? = null,
    val hasLogo: Boolean,
    val state: PluginState,
    val priority: Int,
    val trustLevel: PluginTrustLevel,
)