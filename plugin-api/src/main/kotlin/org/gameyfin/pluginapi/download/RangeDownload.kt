package org.gameyfin.pluginapi.download

import java.io.Closeable
import java.io.InputStream

/** The caller owns this resource and must close it even when no body is sent. */
interface RangeDownload : Closeable {
    val metadata: DownloadMetadata

    /** Quoted strong HTTP entity tag. Providers must document their file mutation assumptions. */
    val etag: String

    /** Opens one bounded stream without reading the preceding bytes. Closing it closes this resource. */
    fun openStream(offset: Long, length: Long): InputStream
}
