package com.ehrplatform.fhir.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration with two profiles:
 *
 * <ul>
 *   <li><b>prod</b> — OAuth2 resource server: validates SMART-on-FHIR JWTs.</li>
 *   <li><b>non-prod</b> (dev/test/docker) — open access so the stack runs without
 *       standing up an identity provider. Clearly logged at startup.</li>
 * </ul>
 *
 * Splitting by profile keeps prod locked down while keeping local
 * {@code docker compose up} and the test suite friction-free.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String[] PUBLIC = {
            "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
            "/fhir/metadata", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Bean
    @Profile("prod")
    public SecurityFilterChain prodFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @Profile("!prod")
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        log.warn("SECURITY: running with OPEN access (non-prod profile). "
                + "Do NOT use this profile with real PHI.");
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
