package de.grimsi.gameyfin.core

import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

class Utils {
    companion object {
        fun maskEmail(email: String): String {
            val regex = """(?:\G(?!^)|(?<=^[^@]{2}|@))[^@](?!\.[^.]+$)""".toRegex()
            return email.replace(regex, "*")
        }

        fun getBaseUrl(): String {
            val request = (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).request
            val scheme = request.scheme
            val serverName = request.serverName
            val serverPort = request.serverPort

            return if (serverPort == 80 || serverPort == 443) {
                "$scheme://$serverName"
            } else {
                "$scheme://$serverName:$serverPort"
            }
        }
    }
}