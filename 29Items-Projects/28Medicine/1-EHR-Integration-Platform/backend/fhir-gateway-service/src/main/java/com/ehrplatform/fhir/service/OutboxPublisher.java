package com.ehrplatform.fhir.service;

import com.ehrplatform.fhir.domain.OutboxEvent;
import com.ehrplatform.fhir.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;

/**
 * Transactional-outbox writer.
 *
 * <p>{@link #enqueue} persists an {@link OutboxEvent} inside the caller's
 * (already-open) DB transaction, so the event commits atomically with the
 * resource change. The {@link OutboxRelay} later publishes committed rows to
 * Kafka — at-least-once delivery, no dual-write.
 */
@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxRepository;

    public OutboxPublisher(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public void enqueue(String eventType, String resourceType, String resourceId, String payloadJson) {
        outboxRepository.save(new OutboxEvent(eventType, resourceType, resourceId, payloadJson));
    }
}
