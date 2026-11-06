package org.gameyfin.pluginapi.download

import org.pf4j.ExtensionPoint
import java.nio.file.Path

/**
 * Extension point for providing downloadable resources.
 *
 * Implementations of this interface are responsible for handling download requests for specific paths.
 * This is typically used to allow plugins to serve files or links for download.
 */
interface DownloadProvider : ExtensionPoint {

    /**
     * Downloads a resource for the given path.
     *
     * @param path The path to the resource to download.
     * @return A [Download] representing the downloadable resource (file or link).
     */
    fun download(path: Path): Download
}