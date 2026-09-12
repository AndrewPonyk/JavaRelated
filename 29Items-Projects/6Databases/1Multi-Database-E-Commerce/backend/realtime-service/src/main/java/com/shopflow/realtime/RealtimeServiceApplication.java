package com.shopflow.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Realtime service entry point. Subscribes to Kafka domain events and pushes
 * them to connected clients over STOMP/WebSocket (order status, price drops,
 * low-stock). This is the "real-time updates" capability from the brief.
 */
@SpringBootApplication
public class RealtimeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeServiceApplication.class, args);
    }
}
