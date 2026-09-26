package com.shiningcrescent.config;

import com.shiningcrescent.security.BearerAuthEntryPoint;
import com.shiningcrescent.security.GoogleOAuthFailureHandler;
import com.shiningcrescent.security.GoogleOAuthSuccessHandler;
import com.shiningcrescent.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final BearerAuthEntryPoint bearerAuthEntryPoint;
    private final GoogleOAuthSuccessHandler googleOAuthSuccessHandler;
    private final GoogleOAuthFailureHandler googleOAuthFailureHandler;

    @Value("${app.cors.origins}")
    private String origins;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(e -> e.authenticationEntryPoint(bearerAuthEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/otp/send",
                                "/api/auth/otp/verify",
                                "/api/auth/public-config",
                                "/media/**",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/.well-known/openid-configuration",
                                "/.well-known/oauth-authorization-server",
                                "/.well-known/jwks.json",
                                "/actuator/health").permitAll()
                        .requestMatchers("/api/payments/stripe/webhook").permitAll()
                        .requestMatchers("/api/agent/wapi/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(a -> a.baseUri("/oauth2/authorization"))
                        .redirectionEndpoint(r -> r.baseUri("/login/oauth2/code/*"))
                        .successHandler(googleOAuthSuccessHandler)
                        .failureHandler(googleOAuthFailureHandler))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.asList(origins.split(",")));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
