package com.example.orderservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API security (see ARCHITECTURE.md §2.5).
 *
 * <p>Two mutually exclusive filter chains, selected by configuration — code never
 * branches on environment names:</p>
 * <ul>
 *   <li><b>JWT resource server</b> — active when
 *       {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} is set
 *       (staging/production via the Helm ConfigMap once an IdP exists).
 *       Mutations require scope {@code orders:write}, reads {@code orders:read}.</li>
 *   <li><b>Open fallback</b> — no issuer configured (local dev, docker-compose, tests).
 *       Still stateless and CSRF-free like the real chain, so behavior differences
 *       between environments stay minimal.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ISSUER_PROPERTY = "spring.security.oauth2.resourceserver.jwt.issuer-uri";

    /** Unauthenticated surface: probes, metrics scrape, API docs, the ops console. */
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
            "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
            "/", "/index.html", "/favicon.ico",
    };

    @Bean
    @ConditionalOnProperty(name = ISSUER_PROPERTY)
    public SecurityFilterChain jwtSecurityChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())   // stateless bearer-token API
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/**").hasAuthority("SCOPE_orders:read")
                        .requestMatchers("/api/v1/orders/**").hasAuthority("SCOPE_orders:write")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * Matches ONLY when the issuer property is absent: {@code matchIfMissing = true}
     * plus a {@code havingValue} no real issuer URI can equal.
     */
    @Bean
    @ConditionalOnProperty(name = ISSUER_PROPERTY, havingValue = "property-absent-guard", matchIfMissing = true)
    public SecurityFilterChain openSecurityChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
