package org.gameyfin.app.core.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Conditional
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint

@Conditional(SsoEnabledCondition::class)
class CustomAuthenticationEntryPoint : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException?
    ) {
        if (request.getParameter("direct") == "1") {
            response.sendRedirect("/login")
        } else {
            response.sendRedirect("/oauth2/authorization/${SecurityConfig.SSO_PROVIDER_KEY}")
        }
    }
}