package org.gameyfin.app.core.plugins.marketplace

import org.pf4j.update.PluginInfo

/**
 * Extends the PF4J [PluginInfo] with additional, Gameyfin specific metadata from the marketplace index.
 */
class GameyfinPluginInfo : PluginInfo() {
    var shortDescription: String? = null
    var license: String? = null

    /**
     * Absolute URL of the plugin logo (resolved relative to the index file)
     */
    var logoUrl: String? = null

    /**
     * Extension point types implemented by the plugin (e.g. "GameMetadataProvider")
     */
    var types: List<String> = emptyList()
    var tags: List<String> = emptyList()

    val gameyfinReleases: List<GameyfinPluginRelease>
        get() = releases.orEmpty().filterIsInstance<GameyfinPluginRelease>()
}

class GameyfinPluginRelease : PluginInfo.PluginRelease() {
    /**
     * Changelog of this release in Markdown format
     */
    var changelog: String? = null
}
