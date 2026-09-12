package com.example.inventory.warehouse;

import java.time.Instant;
import java.util.UUID;

public record WarehouseResponse(
        UUID id,
        String code,
        String name,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}

