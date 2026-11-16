package org.gameyfin.pluginapi.download

/** Metadata available without generating a download body. A null size indicates an unknown length. */
data class DownloadMetadata(val fileExtension: String?, val size: Long?)
