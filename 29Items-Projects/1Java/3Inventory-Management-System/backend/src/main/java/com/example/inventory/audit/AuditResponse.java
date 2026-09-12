package com.example.inventory.audit;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record AuditResponse(
        UUID id,
        String entityType,
        UUID entityId,
        String action,
        String actor,
        String reason,
        String correlationId,
        JsonNode beforeState,
        JsonNode afterState,
        Instant occurredAt) {
}

