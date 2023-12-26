package de.grimsi.gameyfin.rest;


import de.grimsi.gameyfin.service.SetupService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Order(1)
@Component
@RequiredArgsConstructor
public class SetupFilter implements Filter {

    private final SetupService setupService;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse res = (HttpServletResponse) servletResponse;

        boolean isSetupUri = req.getRequestURI().contains("/v1/setup");

        if (setupService.isSetupCompleted() && !isSetupUri ||
                !setupService.isSetupCompleted() && isSetupUri) {
            filterChain.doFilter(req, res);
        } else {
            res.setStatus(HttpStatus.FORBIDDEN.value());
        }
    }
}
