package de.grimsi.gameyfin.pluginapi.core

import org.pf4j.Plugin
import org.pf4j.PluginWrapper

abstract class GameyfinPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    companion object {
        const val LOGO_FILE_NAME: String = "logo"
        val SUPPORTED_LOGO_FORMATS: List<String> = listOf("png", "jpg", "jpeg", "gif", "svg", "webp")
    }

    abstract val configMetadata: List<PluginConfigElement>
    protected open var config: Map<String, String?> = emptyMap()

    open fun getCurrentConfig(): Map<String, String?> {
        return config
    }

    open fun loadConfig(config: Map<String, String?>) {
        this.config = config
    }

    open fun validateConfig(): Boolean {
        return validateConfig(config)
    }

    abstract fun validateConfig(config: Map<String, String?>): Boolean

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