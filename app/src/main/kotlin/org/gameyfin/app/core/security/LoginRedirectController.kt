package org.gameyfin.app.core.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.setup.SetupService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Controller to handle login redirects properly for both SSO and direct login.
 * This replaces the previous hack of using a non-existent endpoint that returns 401.
 */
@Controller
class LoginRedirectController(
    private val config: ConfigService,
    private val setupService: SetupService
) {

    @GetMapping("/loginredirect")
    fun loginRedirect(request: HttpServletRequest, response: HttpServletResponse) {
        if (!setupService.isSetupCompleted()) {
            LoginReturnUrl.save(request, null)
            response.sendRedirect("/setup")
            return
        }
        LoginReturnUrl.save(request, request.getParameter("continue"))
        val directParam = request.getParameter("direct")

        // Check if SSO is enabled
        val isSsoEnabled = config.get(ConfigProperties.SSO.OIDC.Enabled) == true

        if (isSsoEnabled && directParam != "1") {
            // The return destination stays in the session during the OIDC round trip.
            val ssoUrl = "/oauth2/authorization/${SecurityConfig.SSO_PROVIDER_KEY}"
            response.sendRedirect(ssoUrl)
        } else {
            response.sendRedirect("/login")
        }
    }

    @GetMapping("/login/success")
    fun loginSuccess(request: HttpServletRequest, response: HttpServletResponse) {
        response.sendRedirect(LoginReturnUrl.consume(request))
    }
}

