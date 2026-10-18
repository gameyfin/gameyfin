package org.gameyfin.plugins.download.torrent

@Suppress("EnumEntryName")
enum class TorrentDiskIoMode {
    /** Portable file I/O: reopens the file for every block. Slow on network storage. */
    POSIX,

    /**
     * libtorrent's default memory-mapped I/O. Much faster, but libtorrent then installs its own
     * SIGSEGV/SIGBUS handler, so the JVM must run with signal chaining (LD_PRELOAD libjsig.so).
     */
    `Memory-mapped`
}
