package com.ehrplatform.fhir.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional-outbox row. Written in the same DB transaction as the resource
 * change; a scheduled relay publishes unsent rows to Kafka and stamps
 * {@code sentAt}. Guarantees at-least-once delivery with no dual-write loss.
 */
@Entity
@Table(name = "OUTBOX_EVENT")
public class OutboxEvent {

    @Id
    @Column(name = "EVENT_ID", length = 64)
    private String eventId;

    @Column(name = "EVENT_TYPE", nullable = false, length = 128)
    private String eventType;

    @Column(name = "RESOURCE_TYPE", nullable = false, length = 64)
    private String resourceType;

    @Column(name = "RESOURCE_ID", nullable = false, length = 64)
    private String resourceId;

    @Lob
    @Column(name = "PAYLOAD_JSON", nullable = false)
    private String payloadJson;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "SENT_AT")
    private Instant sentAt;

    protected OutboxEvent() {
        // JPA
    }

    public OutboxEvent(String eventType, String resourceType, String resourceId, String payloadJson) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payloadJson = payloadJson;
        this.createdAt = Instant.now();
    }

    public void markSent() {
        this.sentAt = Instant.now();
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
