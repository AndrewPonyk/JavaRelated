package com.example.inventory.messaging;

import java.time.Instant;
import java.util.UUID;

public record StockChangedEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        UUID inventoryItemId,
        UUID warehouseId,
        String sku,
        long quantity,
        long reservedQuantity,
        long availableQuantity,
        long reorderPoint,
        boolean lowStock,
        Instant occurredAt,
        String actor,
        String correlationId) {
}
