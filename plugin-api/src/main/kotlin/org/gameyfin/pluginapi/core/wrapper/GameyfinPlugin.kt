package org.gameyfin.pluginapi.core.wrapper

import org.gameyfin.pluginapi.data.GameyfinDataService
import org.pf4j.Plugin
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.fileSize

/**
 * Abstract base class for all Gameyfin plugins.
 *
 * This class extends the PF4J [Plugin] class and provides access to the [PluginContext] as well as utility methods
 * for state persistence and plugin logo management.
 *
 * Subclasses must declare a public constructor with a single [PluginContext] parameter, which Gameyfin uses to
 * instantiate the plugin:
 * ```
 * class MyPlugin(context: PluginContext) : GameyfinPlugin(context)
 * ```
 *
 * @constructor Creates a Gameyfin plugin with the given [PluginContext].
 * @param context The plugin context provided by the Gameyfin application.
 */
abstract class GameyfinPlugin(val context: PluginContext) : Plugin() {

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
     * Plugin data directory for storing plugin-specific files.
     */
    val dataDirectory: Path
        get() = context.dataDirectory

    /**
     * State file for the plugin, used to persist plugin-specific state.
     */
    val stateFile: Path
        get() = dataDirectory.resolve("state.json")

    /**
     * Read-only access to games, libraries and collections of Gameyfin.
     */
    val dataService: GameyfinDataService
        get() = context.dataService

    /**
     * JSON serializer for serializing and deserializing plugin state.
     */
    val objectMapper: ObjectMapper = jsonMapper {
        addModule(kotlinModule())
    }

    /**
     * Checks if the plugin contains a logo file in any supported format.
     *
     * @return True if a logo file is found, false otherwise.
     */
    fun hasLogo(): Boolean {
        for (format in SUPPORTED_LOGO_FORMATS) {
            val resourcePath = "$LOGO_FILE_NAME.$format"
            if (javaClass.classLoader.getResource(resourcePath) != null) return true
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
            val inputStream = javaClass.classLoader.getResourceAsStream(resourcePath)
            if (inputStream != null) return inputStream.use { it.readAllBytes() }
        }
        return null
    }

    inline fun <reified T> loadState(): T? {
        if (!stateFile.exists() || stateFile.fileSize() == 0L) return null
        return try {
            Files.newBufferedReader(stateFile).use {
                objectMapper.readValue(it.readText(), T::class.java)
            }
        } catch (_: Exception) {
            null
        }
    }

    inline fun <reified T> saveState(state: T) {
        try {
            // Ensure parent directories always exist
            Files.createDirectories(stateFile.parent)
            // Write JSON, creating or truncating the file atomically
            Files.newBufferedWriter(
                stateFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            ).use {
                it.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state))
            }
        } catch (e: IOException) {
            throw IllegalStateException("Failed to persist plugin state to $stateFile", e)
        }
    }
}