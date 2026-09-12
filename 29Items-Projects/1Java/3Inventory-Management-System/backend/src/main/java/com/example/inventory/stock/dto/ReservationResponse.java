package com.example.inventory.stock.dto;

import com.example.inventory.stock.domain.ReservationStatus;
import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID inventoryItemId,
        long quantity,
        ReservationStatus status,
        String externalReference,
        String reason,
        String actor,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}

