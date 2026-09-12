package com.example.inventory.stock.dto;

import com.example.inventory.stock.domain.MovementType;
import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        UUID inventoryItemId,
        UUID relatedItemId,
        MovementType type,
        long quantityDelta,
        long quantityBefore,
        long quantityAfter,
        String reason,
        String referenceType,
        String referenceId,
        String actor,
        Instant occurredAt) {
}

