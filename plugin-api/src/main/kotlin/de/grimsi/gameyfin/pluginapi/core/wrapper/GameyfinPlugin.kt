package de.grimsi.gameyfin.pluginapi.core.wrapper

import org.pf4j.Plugin
import org.pf4j.PluginWrapper

@Suppress("DEPRECATION")
abstract class GameyfinPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    companion object {
        const val LOGO_FILE_NAME: String = "logo"
        val SUPPORTED_LOGO_FORMATS: List<String> = listOf("png", "jpg", "jpeg", "gif", "svg", "webp")

        lateinit var plugin: GameyfinPlugin
            private set
    }

    init {
        plugin = this
    }

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