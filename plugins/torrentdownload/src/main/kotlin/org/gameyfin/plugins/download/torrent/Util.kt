package org.gameyfin.plugins.download.torrent

import kotlin.time.Duration


fun Duration.asHumanReadable(): String {
    return this.toComponents { days, hours, minutes, seconds, _ ->
        buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0 || hours > 0) append("${minutes}m ")
            append("${seconds}s")
        }.trim()
    }
}

fun getContainerOS(): String {
    try {
        val process = Runtime.getRuntime().exec(arrayOf("cat", "/etc/os-release"))
        val output = process.inputStream.bufferedReader().readText()
        val lines = output.lines()
        for (line in lines) {
            if (line.startsWith("ID=")) {
                return line.substringAfter("=")
            }
        }
    } catch (_: Exception) {
    }
    return "unknown"
}