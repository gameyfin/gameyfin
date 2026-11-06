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

    /**
     * Returns metadata without starting a download body, for example for HTTP HEAD requests.
     * Providers may return null when metadata is unavailable without generating the download.
     */
    fun describe(path: Path): DownloadMetadata? = null

    /**
     * Optionally prepares a stable resource that supports byte ranges for pausing and resuming downloads.
     * Returns null when this provider or the requested resource does not support ranges; callers
     * then use [download] to start a full download.
     *
     * The caller owns the returned resource and must close it even when no body is sent.
     */
    fun prepareRangeDownload(path: Path): RangeDownload? = null
}
