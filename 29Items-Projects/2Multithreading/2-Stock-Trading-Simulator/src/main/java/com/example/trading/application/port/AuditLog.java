package com.example.trading.application.port;

import com.example.trading.domain.AuditEvent;
import com.example.trading.domain.AuditType;
import java.time.Instant;
import java.util.List;

public interface AuditLog {
    AuditEvent record(AuditType type, String entityId, String details, Instant occurredAt);

    List<AuditEvent> findAll();
}
