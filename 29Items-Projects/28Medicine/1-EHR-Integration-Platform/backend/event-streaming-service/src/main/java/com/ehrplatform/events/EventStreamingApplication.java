package com.ehrplatform.events;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Event Streaming Service — the Kafka backbone.
 *
 * <p>Produces and consumes {@link com.ehrplatform.common.dto.EhrEventEnvelope}
 * messages: builds read-model projections, relays to downstream subscribers, and
 * manages retry/DLQ topology. Topics are partitioned by patient id to preserve
 * per-patient ordering.
 */
@SpringBootApplication
public class EventStreamingApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventStreamingApplication.class, args);
    }
}
