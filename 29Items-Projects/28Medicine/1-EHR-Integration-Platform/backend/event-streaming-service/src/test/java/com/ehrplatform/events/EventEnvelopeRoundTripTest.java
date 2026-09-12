package com.ehrplatform.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.ehrplatform.common.dto.EhrEventEnvelope;
import com.ehrplatform.events.service.ProjectionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Broker-free integration test for the event pipeline: it exercises the exact
 * wire contract used on Kafka — the producer/relay serializes an
 * {@link EhrEventEnvelope} to JSON, the consumer deserializes it, and the
 * {@link ProjectionStore} applies it. This proves producer↔consumer
 * compatibility without standing up a Kafka broker.
 *
 * <p>(A full {@code @EmbeddedKafka} broker test is the CI counterpart — see the
 * note in this module's pom.xml.)
 */
class EventEnvelopeRoundTripTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void produceSerializeConsumeProject() throws Exception {
        EhrEventEnvelope original = new EhrEventEnvelope(
                "e1", "ehr.patient.created", "Patient", "p1",
                "fhir-gateway-service", Instant.parse("2024-01-01T12:00:00Z"), "trace-1", "{}");

        // Producer/relay side: serialize to JSON (String value on the wire).
        String wire = objectMapper.writeValueAsString(original);

        // Consumer side: deserialize back to the envelope.
        EhrEventEnvelope decoded = objectMapper.readValue(wire, EhrEventEnvelope.class);
        assertThat(decoded).isEqualTo(original);

        // Projection side: applying it builds the read model.
        ProjectionStore store = new ProjectionStore();
        assertThat(store.apply(decoded)).isTrue();
        assertThat(store.findPatient("p1")).isPresent();
        assertThat(store.findPatient("p1").orElseThrow().getLastEventType())
                .isEqualTo("ehr.patient.created");
    }
}
