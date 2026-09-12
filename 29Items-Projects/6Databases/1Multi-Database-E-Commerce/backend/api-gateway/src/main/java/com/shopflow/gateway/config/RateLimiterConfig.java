package com.shopflow.gateway.config;

import java.util.Optional;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

/**
 * Rate-limit key strategy for the Redis-backed {@code RequestRateLimiter}
 * filter: authenticated calls are limited per user (JWT subject), anonymous
 * calls per client IP.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver rateLimitKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .switchIfEmpty(Mono.just(clientIp(exchange)));
    }

    private static String clientIp(org.springframework.web.server.ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("anonymous");
    }
}
