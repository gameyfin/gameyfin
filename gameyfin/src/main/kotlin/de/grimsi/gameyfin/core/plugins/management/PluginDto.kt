package de.grimsi.gameyfin.core.plugins.management

import org.pf4j.PluginState

data class PluginDto(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val state: PluginState
)