package com.ehrplatform.events.consumer;

import com.ehrplatform.common.dto.EhrEventEnvelope;
import com.ehrplatform.events.config.KafkaTopics;
import com.ehrplatform.events.service.ProjectionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Consumes patient lifecycle events and builds the read-model projection.
 *
 * <p>Delivery is at-least-once, so processing is idempotent (the
 * {@link ProjectionStore} dedupes on {@code eventId}). Transient failures are
 * retried with exponential backoff; exhausted messages land on a Dead-Letter
 * Topic handled by {@link #dlt}.
 */
@Component
public class EhrEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EhrEventConsumer.class);

    private final ProjectionStore projectionStore;

    public EhrEventConsumer(ProjectionStore projectionStore) {
        this.projectionStore = projectionStore;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(topics = KafkaTopics.PATIENT_EVENTS, groupId = "projection-builder")
    public void onPatientEvent(EhrEventEnvelope event) {
        if (event.eventId() == null || event.resourceId() == null) {
            // Permanent error: malformed event -> straight to DLT (don't retry).
            throw new IllegalArgumentException("Malformed event: missing id");
        }
        boolean applied = projectionStore.apply(event);
        if (applied) {
            log.info("Projected {} for patient {}", event.eventType(), event.resourceId());
        } else {
            log.debug("Skipped duplicate event {}", event.eventId());
        }
    }

    @DltHandler
    public void dlt(EhrEventEnvelope event) {
        // Terminal: record for human review / replay after a fix.
        log.error("DLT: giving up on event {} ({}/{})",
                event.eventId(), event.resourceType(), event.resourceId());
    }
}
