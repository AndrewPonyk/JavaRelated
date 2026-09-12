package com.example.inventory.common.web;

import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class RequestContext {

    public String actor() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .filter(name -> !name.isBlank())
                .orElse("system");
    }

    public String correlationId() {
        return Optional.ofNullable(MDC.get("correlationId")).orElse("system");
    }
}

