package org.gameyfin.app.core.security

import com.vaadin.flow.spring.security.VaadinWebSecurity
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler


@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val environment: Environment,
    private val config: ConfigService,
    private val ssoAuthenticationSuccessHandler: SsoAuthenticationSuccessHandler,
    private val sessionRegistry: SessionRegistry
) : VaadinWebSecurity() {

    companion object {
        const val SSO_PROVIDER_KEY = "oidc"
    }

    @Throws(Exception::class)
    override fun configure(http: HttpSecurity) {

        // Configure your static resources with public access before calling super.configure(HttpSecurity) as it adds final anyRequest matcher
        http.authorizeHttpRequests { auth: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry ->
            auth.requestMatchers("/login").permitAll()
                .requestMatchers("/setup").permitAll()
                .requestMatchers("/reset-password").permitAll()
                .requestMatchers("/accept-invitation").permitAll()
                .requestMatchers("/public/**").permitAll()

            // Dynamic public access for certain endpoints
            auth.requestMatchers("/").access(DynamicPublicAccessAuthorizationManager(config))
                .requestMatchers("/game/**").access(DynamicPublicAccessAuthorizationManager(config))
                .requestMatchers("/library/**").access(DynamicPublicAccessAuthorizationManager(config))
                .requestMatchers("/search/**").access(DynamicPublicAccessAuthorizationManager(config))
                .requestMatchers("/download/**").access(DynamicPublicAccessAuthorizationManager(config))
                .requestMatchers("/images/**").access(DynamicPublicAccessAuthorizationManager(config))
                .requestMatchers("/images/**").access(DynamicPublicAccessAuthorizationManager(config))
        }

        http.sessionManagement { sessionManagement ->
            sessionManagement
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(3)
                .sessionRegistry(sessionRegistry)
        }

        // Not needed since the frontend is served by the backend
        http.cors { cors -> cors.disable() }

        super.configure(http)

        setLoginView(http, "/login", "/")

        if (config.get(ConfigProperties.SSO.OIDC.Enabled) == true) {
            // Use custom success handler to handle user registration
            http.oauth2Login { oauth2Login -> oauth2Login.successHandler(ssoAuthenticationSuccessHandler) }
            // Prevent unnecessary redirects
            http.logout { logout -> logout.logoutSuccessHandler((HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK))) }

            // Custom authentication entry point to support SSO and direct login
            http.exceptionHandling { exceptionHandling ->
                exceptionHandling.authenticationEntryPoint(CustomAuthenticationEntryPoint())
            }
        }
    }

    @Throws(Exception::class)
    public override fun configure(web: WebSecurity) {
        super.configure(web)

        if ("dev" in environment.activeProfiles) {
            web.ignoring().requestMatchers("/h2-console/**")
        }
    }

    @Bean
    @Conditional(SsoEnabledCondition::class)
    fun clientRegistrationRepository(): ClientRegistrationRepository? {
        val clientRegistration = ClientRegistration.withRegistrationId(SSO_PROVIDER_KEY)
            .clientId(config.get(ConfigProperties.SSO.OIDC.ClientId))
            .clientSecret(config.get(ConfigProperties.SSO.OIDC.ClientSecret))
            .scope("openid", "profile", "email")
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