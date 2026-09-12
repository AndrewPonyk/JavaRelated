package com.example.trading.infrastructure.concurrent;

import com.example.trading.application.port.AuditLog;
import com.example.trading.domain.AuditEvent;
import com.example.trading.domain.AuditType;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class ConcurrentAuditLog implements AuditLog {
    private final AtomicLong sequence = new AtomicLong(1L);
    private final ConcurrentLinkedQueue<AuditEvent> events = new ConcurrentLinkedQueue<>();

    @Override
    public AuditEvent record(AuditType type, String entityId, String details, Instant occurredAt) {
        long id = sequence.getAndIncrement();
        if (id <= 0) {
            throw new IllegalStateException("audit sequence exhausted");
        }
        AuditEvent event = new AuditEvent(id, type, entityId, details, occurredAt);
        events.add(event);
        return event;
    }

    @Override
    public List<AuditEvent> findAll() {
        return events.stream()
                .sorted(Comparator.comparingLong(AuditEvent::sequence))
                .toList();
    }
}
