package de.grimsi.gameyfinplugins.torrentdownload

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