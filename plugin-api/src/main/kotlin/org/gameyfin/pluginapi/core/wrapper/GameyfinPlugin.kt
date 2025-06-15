package org.gameyfin.pluginapi.core.wrapper

import org.pf4j.Plugin
import org.pf4j.PluginWrapper

/**
 * Abstract base class for all Gameyfin plugins.
 *
 * This class extends the PF4J [Plugin] class and provides utility methods for plugin logo management.
 * It also maintains a static reference to the current plugin instance.
 *
 * @constructor Creates a Gameyfin plugin with the given [PluginWrapper].
 * @param wrapper The plugin wrapper provided by the Gameyfin application.
 */
@Suppress("DEPRECATION")
abstract class GameyfinPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    companion object {
        /**
         * The base name of the logo file (without extension).
         */
        const val LOGO_FILE_NAME: String = "logo"

        /**
         * Supported logo file formats.
         */
        val SUPPORTED_LOGO_FORMATS: List<String> = listOf("png", "jpg", "jpeg", "gif", "svg", "webp")

        /**
         * Reference to the current plugin instance.
         */
        lateinit var plugin: GameyfinPlugin
            private set
    }

    /**
     * Initializes the plugin and sets the static plugin reference.
     */
    init {
        plugin = this
    }

    /**
     * Checks if the plugin contains a logo file in any supported format.
     *
     * @return True if a logo file is found, false otherwise.
     */
    fun hasLogo(): Boolean {
        for (format in SUPPORTED_LOGO_FORMATS) {
            val resourcePath = "$LOGO_FILE_NAME.$format"
            val inputStream = wrapper.pluginClassLoader.getResourceAsStream(resourcePath)
            if (inputStream != null) {
                return true
            }
        }

        return false
    }

    /**
     * Retrieves the logo file as a byte array, if present.
     *
     * @return The logo file as a byte array, or null if not found.
     */
    fun getLogo(): ByteArray? {
        for (format in SUPPORTED_LOGO_FORMATS) {
            val resourcePath = "$LOGO_FILE_NAME.$format"
            val inputStream = wrapper.pluginClassLoader.getResourceAsStream(resourcePath)
            if (inputStream != null) {
                return inputStream.readAllBytes()
            }
        }

        return null
    }
}