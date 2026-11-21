package org.gameyfin.pluginapi.download

import java.io.InputStream

/**
 * Represents a downloadable resource, which can be either a file or a link.
 */
sealed interface Download

/**
 * Represents a file-based download.
 *
 * @property data The input stream containing the file data.
 * @property fileExtension The file extension (e.g., "zip", "png"), or null if none.
 * @property size The size of the file in bytes, or null if unknown.
 * @property fileName Original basename for a file returned directly from a selection.
 */
@OptIn(ExperimentalVersionOverloading::class)
data class FileDownload(
    val data: InputStream,
    val fileExtension: String? = null,
    val size: Long? = null,
    @IntroducedAt("2.7.1") val fileName: String? = null
) : Download

/**
 * Represents a link-based download.
 *
 * @property url The URL to the downloadable resource.
 */
data class LinkDownload(
    val url: String
) : Download
