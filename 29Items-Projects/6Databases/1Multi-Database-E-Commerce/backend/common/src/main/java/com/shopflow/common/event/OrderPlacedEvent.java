package com.shopflow.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cross-service domain event emitted by order-service when an order is committed.
 *
 * <p>Consumed by recommendation-service (build {@code BOUGHT} graph edges) and
 * realtime-service (push status to the customer). The envelope fields
 * ({@code eventId}, {@code occurredAt}, {@code version}, {@code correlationId})
 * are common to every ShopFlow event and enable idempotent, traceable consumers.
 *
 * <p>Schema is published to the registry; evolve only in backward-compatible ways.
 */
public record OrderPlacedEvent(
        UUID eventId,
        String correlationId,
        int version,
        Instant occurredAt,
        // ---- payload ----
        String orderId,
        String customerId,
        BigDecimal totalAmount,
        String currency,
        List<Line> lines) {

    public static final String TOPIC = "order.events";
    public static final String TYPE = "OrderPlaced";

    /** One ordered product line. */
    public record Line(String productId, int quantity, BigDecimal unitPrice) { }
}
