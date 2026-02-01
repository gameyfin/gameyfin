package org.gameyfin.app.core.security

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer
import com.vaadin.hilla.route.RouteUtil
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler


@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val environment: Environment,
    private val config: ConfigService,
    private val ssoAuthenticationSuccessHandler: SsoAuthenticationSuccessHandler,
    private val sessionRegistry: SessionRegistry
) {

    companion object {
        const val SSO_PROVIDER_KEY = "oidc"
    }

    @Bean
    fun filterChain(http: HttpSecurity, routeUtil: RouteUtil): SecurityFilterChain {
        // Apply Vaadin configuration first to properly configure CSRF and request matchers
        if (config.get(ConfigProperties.SSO.OIDC.Enabled) == true) {
            http.with(VaadinSecurityConfigurer.vaadin()) { configurer ->
                // Redirect to SSO provider on logout
                configurer.loginView("/login", config.get(ConfigProperties.SSO.OIDC.LogoutUrl))
            }

            // Use custom success handler to handle user registration
            http.oauth2Login { oauth2Login ->
                oauth2Login.successHandler(ssoAuthenticationSuccessHandler)
            }
            // Prevent unnecessary redirects
            http.logout { logout -> logout.logoutSuccessHandler((HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK))) }

            // Custom authentication entry point to support SSO and direct login
            http.exceptionHandling { exceptionHandling ->
                exceptionHandling.authenticationEntryPoint(CustomAuthenticationEntryPoint())
            }
        } else {
            // Use default Vaadin login URLs
            http.with(VaadinSecurityConfigurer.vaadin()) { configurer ->
                configurer.loginView("/login")
            }
        }

        http.authorizeHttpRequests { auth ->
            // Set default security policy that permits Hilla internal requests and denies all other
            auth.requestMatchers(routeUtil::isRouteAllowed).permitAll()
                // Gameyfin static resources and public endpoints
                .requestMatchers(
                    "/login",
                    "/loginredirect",
                    "/setup",
                    "/reset-password",
                    "/accept-invitation",
                    "/public/**",
                    "/images/**",
                    "/favicon.ico",
                    "/favicon.svg"
                ).permitAll()
                // Client-side SPA routes - these need to pass through to serve index.html
                // Authentication will be handled by Hilla on the client side
                .requestMatchers(
                    "/administration/**",
                    "/settings/**",
                    "/collection/**"
                ).permitAll()
                // Dynamic public access for certain endpoints
                .requestMatchers(
                    "/",
                    "/game/**",
                    "/library/**",
                    "/search/**",
                    "/requests/**",
                    "/download/**"
                ).access(DynamicPublicAccessAuthorizationManager(config))
        }

        http.sessionManagement { sessionManagement ->
            sessionManagement
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(3)
                .sessionRegistry(sessionRegistry)
        }

        // Not needed since the frontend is served by the backend
        http.cors { cors -> cors.disable() }



        if ("dev" in environment.activeProfiles) {
            http.authorizeHttpRequests { auth -> auth.requestMatchers("/h2-console/**").permitAll() }
        }

        return http.build()
    }

    @Bean
    @Conditional(SsoEnabledCondition::class)
    fun clientRegistrationRepository(): ClientRegistrationRepository? {
        val clientRegistration = ClientRegistration.withRegistrationId(SSO_PROVIDER_KEY)
            .clientId(config.get(ConfigProperties.SSO.OIDC.ClientId))
            .clientSecret(config.get(ConfigProperties.SSO.OIDC.ClientSecret))
            .scope(config.get(ConfigProperties.SSO.OIDC.OAuthScopes)?.toList())
            .userNameAttributeName("preferred_username")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .issuerUri(config.get(ConfigProperties.SSO.OIDC.IssuerUrl))
            .authorizationUri(config.get(ConfigProperties.SSO.OIDC.AuthorizeUrl))
            .tokenUri(config.get(ConfigProperties.SSO.OIDC.TokenUrl))
            .userInfoUri(config.get(ConfigProperties.SSO.OIDC.UserInfoUrl))
            .jwkSetUri(config.get(ConfigProperties.SSO.OIDC.JwksUrl))
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .build()

        return InMemoryClientRegistrationRepository(clientRegistration)
    }
}