package org.gameyfin.app.core.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint

class CustomAuthenticationEntryPoint : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        val target = request.requestURI + (request.queryString?.let { "?$it" } ?: "")
        LoginReturnUrl.save(request, target)
        response.sendRedirect(
            if (request.getParameter("direct") == "1") "/loginredirect?resume=1&direct=1" else "/loginredirect?resume=1"
        )
    }
}
