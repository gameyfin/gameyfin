package de.grimsi.gameyfin.pluginapi.download

import java.io.InputStream

sealed interface Download

data class FileDownload(
    val data: InputStream,
    val fileExtension: String? = null,
    val size: Long? = null
) : Download

data class LinkDownload(
    val url: String
) : Download

