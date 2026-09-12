package com.shopflow.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Order service entry point.
 *
 * <p>Owns the transactional core of the platform (orders, items, status) in
 * Oracle and publishes {@code OrderPlacedEvent} to Kafka via the outbox relay.
 * Scans the shared {@code com.shopflow.common} package so the platform-wide
 * {@code GlobalExceptionHandler} and DTOs are picked up.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.shopflow.order", "com.shopflow.common"})
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
