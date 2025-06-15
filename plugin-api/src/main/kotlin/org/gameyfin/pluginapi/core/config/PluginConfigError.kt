package org.gameyfin.pluginapi.core.config

/**
 * Exception thrown when there is an error in plugin configuration.
 *
 * This exception is used to indicate problems such as missing, invalid, or malformed configuration values
 * when loading or validating plugin configuration.
 *
 * @param message The detail message describing the configuration error.
 */
class PluginConfigError(message: String) : RuntimeException(message)