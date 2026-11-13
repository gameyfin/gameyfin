package org.gameyfin.app.core.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint

class CustomAuthenticationEntryPoint : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        val target = request.requestURI + (request.queryString?.let { "?$it" } ?: "")
        val continueParam = URLEncoder.encode(target, StandardCharsets.UTF_8)
        val directParam = if (request.getParameter("direct") == "1") "&direct=1" else ""
        response.sendRedirect("/loginredirect?continue=$continueParam$directParam")
    }
}
