package com.shopflow.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted by catalog-service when a customer posts a product review. Consumed by
 * ml-service, which scores sentiment and replies with a {@link ReviewScoredEvent}.
 */
public record ReviewCreatedEvent(
        UUID eventId,
        String correlationId,
        int version,
        Instant occurredAt,
        // ---- payload ----
        String reviewId,
        String productId,
        String author,
        int rating,
        String text) {

    public static final String TOPIC = "review.events";
    public static final String TYPE = "ReviewCreated";
}
