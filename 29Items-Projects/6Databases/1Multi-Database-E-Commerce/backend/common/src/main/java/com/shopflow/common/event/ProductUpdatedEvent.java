package com.shopflow.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Emitted by catalog-service whenever a product is created or changed.
 *
 * <p>Consumed by search-service (project into Elasticsearch) and
 * recommendation-service (maintain product nodes). Keyed by {@code productId}.
 */
public record ProductUpdatedEvent(
        UUID eventId,
        String correlationId,
        int version,
        Instant occurredAt,
        // ---- payload ----
        String productId,
        String name,
        String category,
        BigDecimal price,
        boolean active) {

    public static final String TOPIC = "product.events";
    public static final String TYPE = "ProductUpdated";
}
