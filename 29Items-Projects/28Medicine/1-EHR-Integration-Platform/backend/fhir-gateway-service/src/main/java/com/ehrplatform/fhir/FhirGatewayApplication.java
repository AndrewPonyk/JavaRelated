package com.ehrplatform.fhir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FHIR Gateway Service — HAPI FHIR R4 system-of-record API.
 *
 * <p>Exposes FHIR resources over REST, persists them to Oracle, and publishes
 * resource lifecycle events to Kafka via a transactional outbox relay.
 *
 * <p>{@code @EnableScheduling} drives the outbox poller (see OutboxRelay, P2-3).
 */
@SpringBootApplication
@EnableScheduling
public class FhirGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(FhirGatewayApplication.class, args);
    }
}
