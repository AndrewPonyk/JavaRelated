package com.loanorigination.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Dev-only filter that injects an anonymous authentication with all application
 * roles so that {@code @PreAuthorize} checks pass without a running Keycloak instance.
 *
 * Registered exclusively in the "dev" profile security chain.
 */
public class DevAuthenticationFilter extends OncePerRequestFilter {

    private static final List<SimpleGrantedAuthority> ALL_ROLES = List.of(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("ROLE_LOAN_OFFICER"),
            new SimpleGrantedAuthority("ROLE_UNDERWRITER")
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken("dev-user", null, ALL_ROLES);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
