package com.loanorigination.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts Keycloak JWT realm roles into Spring Security {@code ROLE_*} authorities.
 *
 * Keycloak stores realm-level roles inside the claim:
 * <pre>
 *   { "realm_access": { "roles": ["LOAN_OFFICER", "UNDERWRITER", ...] } }
 * </pre>
 *
 * This converter merges those roles (prefixed with {@code ROLE_}) with the
 * default {@code SCOPE_*} authorities produced by Spring Security.
 */
@Component
public class KeycloakJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> scopeAuthorities = defaultConverter.convert(jwt);
        Collection<GrantedAuthority> realmRoles = extractRealmRoles(jwt);

        Set<GrantedAuthority> merged = Stream.concat(
                scopeAuthorities != null ? scopeAuthorities.stream() : Stream.empty(),
                realmRoles.stream()
        ).collect(Collectors.toSet());

        return new JwtAuthenticationToken(jwt, merged, jwt.getClaimAsString("preferred_username"));
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return Collections.emptyList();
        }

        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof Collection<?>)) {
            return Collections.emptyList();
        }

        return ((Collection<String>) rolesObj).stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }
}
