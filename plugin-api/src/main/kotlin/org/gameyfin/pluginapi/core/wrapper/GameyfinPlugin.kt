package org.gameyfin.pluginapi.core.wrapper

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.fileSize

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
    }

    /**
     * State file for the plugin, used to persist plugin-specific state.
     */
    val stateFile: Path = Path.of("${wrapper.pluginId}.state.json")

    /**
     * JSON serializer for serializing and deserializing plugin state.
     */
    val objectMapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

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

    inline fun <reified T> loadState(): T? {
        if (!stateFile.exists() || stateFile.fileSize() == 0L) return null
        return Files.newBufferedReader(stateFile).use {
            objectMapper.readValue(it.readText(), T::class.java)
        }
    }

    inline fun <reified T> saveState(state: T) {
        if (!stateFile.exists()) stateFile.createFile()
        Files.newBufferedWriter(stateFile).use {
            it.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state))
        }
    }
}