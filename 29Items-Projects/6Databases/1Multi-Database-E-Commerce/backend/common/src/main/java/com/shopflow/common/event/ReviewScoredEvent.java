package com.shopflow.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted by ml-service after scoring a review's sentiment. Consumed by
 * catalog-service (update the review + product rating aggregate) and
 * search-service (boost ranking by sentiment).
 */
public record ReviewScoredEvent(
        UUID eventId,
        String correlationId,
        int version,
        Instant occurredAt,
        // ---- payload ----
        String reviewId,
        String productId,
        String label,
        double score) {

    public static final String TOPIC = "review.scored";
    public static final String TYPE = "ReviewScored";
}
