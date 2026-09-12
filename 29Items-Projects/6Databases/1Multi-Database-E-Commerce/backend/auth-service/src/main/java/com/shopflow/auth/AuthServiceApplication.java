package com.shopflow.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auth service entry point. Issues short-lived JWT access tokens and stores
 * rotating refresh tokens + sessions in Redis (fast, TTL-based expiry).
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.shopflow.auth", "com.shopflow.common"})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
