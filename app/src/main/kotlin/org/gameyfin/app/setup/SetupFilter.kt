package org.gameyfin.app.setup

import jakarta.servlet.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.io.IOException


@Order(1)
@Component
class SetupFilter(
    private val setupService: SetupService
) : Filter {

    @Throws(ServletException::class, IOException::class)
    override fun doFilter(servletRequest: ServletRequest, servletResponse: ServletResponse, filterChain: FilterChain) {
        val req = servletRequest as HttpServletRequest
        val res = servletResponse as HttpServletResponse

        val path = req.requestURI.removePrefix(req.contextPath).trimEnd('/')
        val isSetupUri = path == "/setup"
        val isLoginUri = path == "/login"

        // Skip this filter if the urls don't match
        if (!(isSetupUri || isLoginUri)) {
            filterChain.doFilter(req, res)
            return
        }

        val isSetupComplete = setupService.isSetupCompleted()

        if (isSetupUri && isSetupComplete) {
            res.sendRedirect("/loginredirect")
            return
        } else if (isLoginUri && !isSetupComplete) {
            res.sendRedirect("/setup")
            return
        }

        filterChain.doFilter(req, res)
    }
}
