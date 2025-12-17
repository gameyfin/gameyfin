package org.gameyfin.app.core.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Controller to handle login redirects properly for both SSO and direct login.
 * This replaces the previous hack of using a non-existent endpoint that returns 401.
 */
@Controller
class LoginRedirectController(
    private val config: ConfigService
) {

    @GetMapping("/loginredirect")
    fun loginRedirect(request: HttpServletRequest, response: HttpServletResponse) {
        val continueParam = request.getParameter("continue")
        val directParam = request.getParameter("direct")

        // Check if SSO is enabled
        val isSsoEnabled = config.get(ConfigProperties.SSO.OIDC.Enabled) == true

        if (isSsoEnabled && directParam != "1") {
            // Redirect to SSO provider with continue parameter if present
            val ssoUrl = "/oauth2/authorization/${SecurityConfig.SSO_PROVIDER_KEY}"
            if (!continueParam.isNullOrBlank()) {
                response.sendRedirect("$ssoUrl?continue=$continueParam")
            } else {
                response.sendRedirect(ssoUrl)
            }
        } else {
            // Redirect to direct login page with continue parameter if present
            if (!continueParam.isNullOrBlank()) {
                response.sendRedirect("/login?continue=$continueParam")
            } else {
                response.sendRedirect("/login")
            }
        }
    }
}

