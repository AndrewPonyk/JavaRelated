package com.example.trading.domain;

import java.time.Instant;
import java.util.Objects;

public record AuditEvent(
        long sequence,
        AuditType type,
        String entityId,
        String details,
        Instant occurredAt) {

    public AuditEvent {
        if (sequence <= 0) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        type = Objects.requireNonNull(type, "type");
        entityId = DomainValidation.identifier(entityId, "entityId");
        details = DomainValidation.text(details, "details", 512);
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
