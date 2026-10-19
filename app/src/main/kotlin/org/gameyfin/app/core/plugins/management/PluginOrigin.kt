package org.gameyfin.app.core.plugins.management

enum class PluginOrigin {
    /**
     * Bundled with Gameyfin, mounted manually into the plugins directory or loaded in development mode
     */
    LOCAL,

    /**
     * Installed via the plugin marketplace
     */
    MARKETPLACE,
}
