package de.grimsi.gameyfin.setup

import jakarta.servlet.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import java.io.IOException


//@Order(1)
//@Component
class SetupFilter(
    private val setupService: SetupService
) : Filter {

    @Throws(ServletException::class, IOException::class)
    override fun doFilter(servletRequest: ServletRequest, servletResponse: ServletResponse, filterChain: FilterChain) {
        val req = servletRequest as HttpServletRequest
        val res = servletResponse as HttpServletResponse

        val isSetupUri = req.requestURI.contains("/v1/setup")

        if (setupService.isSetupCompleted() && !isSetupUri ||
            !setupService.isSetupCompleted() && isSetupUri
        ) {
            filterChain.doFilter(req, res)
        } else {
            res.status = HttpStatus.FORBIDDEN.value()
        }
    }
}