package de.grimsi.gameyfin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfiguration {

    @Value("${gameyfin.user}")
    private String username;

    @Value("${gameyfin.password}")
    private String password;

    @Bean
    protected SecurityFilterChain httpSecurity(HttpSecurity http) throws Exception {

        // TODO: Try to enable CSRF
        http.csrf().disable();

        // Allow GET-Requests on *all* URLs (Frontend will handle 404 and permission)
        // except paths under "/v1/library-management"
        http.authorizeRequests()
                .antMatchers("**").permitAll()
                .antMatchers("/v1/library-management").authenticated()
                .anyRequest().denyAll();

        http.httpBasic(Customizer.withDefaults());

        http.exceptionHandling()
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));

        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        UserDetails user = User
                .withDefaultPasswordEncoder()
                .username(username)
                .password(password)
                .authorities("ADMIN_API_ACCESS")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", new CorsConfiguration().applyPermitDefaultValues());
        return source;
    }

}
