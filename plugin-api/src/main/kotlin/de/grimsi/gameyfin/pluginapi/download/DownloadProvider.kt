package de.grimsi.gameyfin.pluginapi.download

import org.pf4j.ExtensionPoint
import java.nio.file.Path

interface DownloadProvider : ExtensionPoint {

    fun download(path: Path): Download
}