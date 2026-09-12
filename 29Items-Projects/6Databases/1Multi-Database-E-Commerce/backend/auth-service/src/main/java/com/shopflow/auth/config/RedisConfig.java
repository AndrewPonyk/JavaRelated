package com.shopflow.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

/**
 * Activates Spring Data Redis repositories so {@code UserRepository}
 * ({@code @RedisHash}) is backed by the Redis instance.
 */
@Configuration
@EnableRedisRepositories(basePackages = "com.shopflow.auth.repository")
public class RedisConfig {
}
