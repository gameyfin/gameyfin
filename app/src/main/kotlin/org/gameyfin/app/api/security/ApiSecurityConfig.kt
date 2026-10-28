package org.gameyfin.app.api.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextHolderStrategy
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken
import org.springframework.security.oauth2.server.resource.web.HeaderBearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationConverter
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
class ApiSecurityConfig {
    @Bean
    @Order(0)
    fun publicApiSecurity(http: HttpSecurity, credentials: ApiCredentialService,
                          strategies: ObjectProvider<SecurityContextHolderStrategy>): SecurityFilterChain {
        val authenticationConverter = BearerTokenAuthenticationConverter().apply {
            setBearerTokenResolver(HeaderBearerTokenResolver("X-API-Key"))
        }
        val filter = BearerTokenAuthenticationFilter(AuthenticationManager { authentication ->
            credentials.authenticate((authentication as BearerTokenAuthenticationToken).token)
        }, authenticationConverter)
        // Vaadin installs its strategy after singleton creation. Match the strategy used by
        // HttpSecurity instead of capturing the earlier global default in this manual filter.
        filter.setSecurityContextHolderStrategy(strategies.getIfAvailable { SecurityContextHolder.getContextHolderStrategy() })
        // Retain authentication across streaming/error dispatches of this request only.
        val contextRepository = RequestAttributeSecurityContextRepository()
        filter.setSecurityContextRepository(contextRepository)
        filter.setAuthenticationEntryPoint { _, response, _ -> problem(response, 401, "unauthorized", "A valid X-API-Key header is required") }
        http.securityMatcher("/v1", "/v1/**")
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .securityContext { it.securityContextRepository(contextRepository) }
            .requestCache { it.disable() }
            .csrf { it.disable() }
            .cors { it.disable() }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ -> problem(response, 401, "unauthorized", "A valid X-API-Key header is required") }
                it.accessDeniedHandler { _, response, _ -> problem(response, 403, "forbidden", "Permission denied") }
            }
            .addFilterBefore(filter, AnonymousAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    @Order(0)
    fun apiDocumentationSecurity(http: HttpSecurity, config: ConfigService): SecurityFilterChain = http
        .securityMatcher("/v3/api-docs/**", "/v3/api-docs.yaml/**", "/swagger-ui/**", "/swagger-ui.html")
        .addFilterBefore(object : OncePerRequestFilter() {
            override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
                if (config.get(ConfigProperties.Security.SwaggerUiEnabled) != true) {
                    response.status = HttpServletResponse.SC_NOT_FOUND
                    response.setHeader("Cache-Control", "no-store")
                    return
                }
                filterChain.doFilter(request, response)
            }
        }, AnonymousAuthenticationFilter::class.java)
        .authorizeHttpRequests { it.anyRequest().permitAll() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .build()

    private fun problem(response: HttpServletResponse, status: Int, code: String, detail: String) {
        response.status = status
        response.contentType = "application/problem+json"
        response.setHeader("Cache-Control", "no-store")
        response.writer.write("""{"type":"about:blank","title":"$code","status":$status,"detail":"$detail","code":"$code"}""")
    }
}
