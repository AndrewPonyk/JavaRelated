package com.ehrplatform.fhir.service;

import com.ehrplatform.common.dto.EhrEventEnvelope;
import com.ehrplatform.fhir.domain.OutboxEvent;
import com.ehrplatform.fhir.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains committed {@link OutboxEvent} rows to Kafka, then marks them sent.
 *
 * <p>This is the second half of the transactional-outbox pattern: the resource
 * write committed the event row atomically; this relay publishes it. Because we
 * only mark a row sent <em>after</em> the broker acknowledges, delivery is
 * at-least-once (a crash between publish and mark re-publishes — consumers
 * dedupe on {@code eventId}).
 *
 * <p>Disabled in tests via {@code ehr.outbox.enabled=false} so no broker is
 * required.
 */
@Component
@ConditionalOnProperty(prefix = "ehr.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxRelay(OutboxEventRepository outboxRepository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${ehr.outbox.relay-interval-ms:2000}")
    public void relay() {
        List<OutboxEvent> batch = outboxRepository.findBySentAtIsNullOrderByCreatedAtAsc()
                .stream().limit(BATCH_SIZE).toList();
        if (batch.isEmpty()) {
            return;
        }
        log.debug("Outbox relay: publishing {} event(s)", batch.size());
        for (OutboxEvent event : batch) {
            try {
                publish(event);
                event.markSent();
                outboxRepository.save(event);
            } catch (Exception e) {
                // Leave unsent; the next tick retries. Never lose the event.
                log.error("Outbox relay failed for event {} — will retry", event.getEventId(), e);
            }
        }
    }

    private void publish(OutboxEvent event) throws Exception {
        EhrEventEnvelope envelope = new EhrEventEnvelope(
                event.getEventId(),
                event.getEventType(),
                event.getResourceType(),
                event.getResourceId(),
                "fhir-gateway-service",
                event.getCreatedAt(),
                null,
                event.getPayloadJson());
        String json = serialize(envelope);
        // Key by resourceId → per-patient/per-resource ordering on one partition.
        kafkaTemplate.send(topicFor(event.getResourceType()), event.getResourceId(), json)
                .get(10, TimeUnit.SECONDS);
    }

    private String serialize(EhrEventEnvelope envelope) throws JsonProcessingException {
        return objectMapper.writeValueAsString(envelope);
    }

    static String topicFor(String resourceType) {
        return "ehr." + resourceType.toLowerCase() + ".events";
    }
}
