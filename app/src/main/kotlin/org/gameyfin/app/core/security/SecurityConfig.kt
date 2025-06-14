package org.gameyfin.app.core.security

import com.vaadin.flow.spring.security.VaadinWebSecurity
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.config.ConfigProperties
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
import org.springframework.web.cors.CorsConfiguration


@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val environment: Environment,
    private val config: ConfigService,
    private val ssoAuthenticationSuccessHandler: SsoAuthenticationSuccessHandler,
    private val sessionRegistry: SessionRegistry
) : VaadinWebSecurity() {

    private val ssoProviderKey: String = "oidc"
    private val allowedOrigins: List<String>? = config.get(ConfigProperties.System.Cors.AllowedOrigins)?.toList()

    @Throws(Exception::class)
    override fun configure(http: HttpSecurity) {

        // Configure your static resources with public access before calling super.configure(HttpSecurity) as it adds final anyRequest matcher
        http.authorizeHttpRequests { auth: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry ->
            auth.requestMatchers("/setup").permitAll()
                .requestMatchers("/reset-password").permitAll()
                .requestMatchers("/accept-invitation").permitAll()
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/images/**").permitAll()
        }

        http.sessionManagement { sessionManagement ->
            sessionManagement
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(3)
                .sessionRegistry(sessionRegistry)
        }

        http.cors { cors ->
            cors.configurationSource { request ->
                val configuration = CorsConfiguration()
                configuration.allowedOrigins = allowedOrigins
                configuration
            }
        }

        super.configure(http)

        if (config.get(ConfigProperties.SSO.OIDC.Enabled) == true) {
            setOAuth2LoginPage(http, "/oauth2/authorization/$ssoProviderKey")
            // Use custom success handler to handle user registration
            http.oauth2Login { oauth2Login -> oauth2Login.successHandler(ssoAuthenticationSuccessHandler) }
            // Prevent unnecessary redirects
            http.logout { logout -> logout.logoutSuccessHandler((HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK))) }
        } else {
            setLoginView(http, "/login", "/")
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
        val clientRegistration = ClientRegistration.withRegistrationId(ssoProviderKey)
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