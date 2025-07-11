package org.gameyfin.plugins.download.torrent

import java.nio.file.Path

data class TorrentDownloadPluginState(
    val torrentFilesMetadata: MutableList<TorrentFileMetadata> = mutableListOf()
)

data class TorrentFileMetadata(
    val torrentFile: Path,
    val gameFile: Path
)
