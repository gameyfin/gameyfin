package de.grimsi.gameyfin.setup

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

        val isSetupUri = req.requestURI.startsWith("/setup")
        val isLoginUri = req.requestURI.startsWith("/login")

        // Skip this filter if the urls don't match
        if (!(isSetupUri || isLoginUri)) {
            filterChain.doFilter(req, res)
            return
        }

        val isSetupComplete = setupService.isSetupCompleted()

        if (isSetupUri && isSetupComplete) {
            res.sendRedirect("/login")
        } else if (isLoginUri && !isSetupComplete) {
            res.sendRedirect("/setup")
        }

        // took me longer than I want to admit to realize you always need to call doFilter() at the end
        filterChain.doFilter(req, res)
    }
}