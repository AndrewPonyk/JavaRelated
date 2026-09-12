package com.shopflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reactive API gateway (Spring Cloud Gateway) — the single ingress for the
 * platform. Responsibilities: route to downstream services, terminate auth
 * (verify JWT), apply CORS + rate limits, and propagate the correlation id.
 * Routes are declared in {@code application.yml}.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
