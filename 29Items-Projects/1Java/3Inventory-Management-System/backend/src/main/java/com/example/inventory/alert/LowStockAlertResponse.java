package com.example.inventory.alert;

import java.time.Instant;
import java.util.UUID;

public record LowStockAlertResponse(
        UUID id, UUID eventId, UUID inventoryItemId, String sku, String itemName,
        String warehouseCode, long quantity, long reorderPoint, AlertStatus status,
        Instant createdAt, Instant acknowledgedAt, String acknowledgedBy) {
}

