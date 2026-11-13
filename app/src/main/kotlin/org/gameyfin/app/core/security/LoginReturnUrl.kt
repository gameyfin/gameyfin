package org.gameyfin.app.core.security

import jakarta.servlet.http.HttpServletRequest
import java.net.URI

/** Stores one local return destination across either form login or an OIDC round trip. */
object LoginReturnUrl {
    private const val SESSION_ATTRIBUTE = "gameyfin.login.returnUrl"

    fun save(request: HttpServletRequest, target: String?) {
        request.getSession(false)?.removeAttribute(SESSION_ATTRIBUTE)
        validate(target)?.let { request.getSession(true).setAttribute(SESSION_ATTRIBUTE, it) }
    }

    fun consume(request: HttpServletRequest): String {
        val session = request.getSession(false) ?: return "/"
        val target = session.getAttribute(SESSION_ATTRIBUTE) as? String
        session.removeAttribute(SESSION_ATTRIBUTE)
        return validate(target) ?: "/"
    }

    private fun validate(target: String?): String? {
        if (target.isNullOrBlank() || target.any { it.isISOControl() } || '\\' in target) return null
        val uri = runCatching { URI(target) }.getOrNull() ?: return null
        val decodedPath = uri.path ?: return null
        if (!decodedPath.startsWith("/") || decodedPath.startsWith("//")) return null
        val path = runCatching { URI(null, null, decodedPath, null).normalize().path }.getOrNull() ?: return null
        if (uri.isAbsolute || uri.rawAuthority != null || !path.startsWith("/") || path.startsWith("//")) return null
        if (path.any { it.isISOControl() } || '\\' in path || ';' in path) return null
        if (path.trimEnd('/') in setOf("/login", "/loginredirect", "/login/success", "/setup", "/logout") ||
            path.startsWith("/oauth2/") || path.startsWith("/login/")) return null
        return target
    }
}
