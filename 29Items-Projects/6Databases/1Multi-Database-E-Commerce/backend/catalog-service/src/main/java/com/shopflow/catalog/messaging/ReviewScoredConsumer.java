package com.shopflow.catalog.messaging;

import com.shopflow.catalog.service.CatalogService;
import com.shopflow.common.event.ReviewScoredEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code review.scored} from ml-service and writes the sentiment back
 * onto the stored review. Idempotent (keyed by reviewId), so redelivery is safe.
 */
@Component
public class ReviewScoredConsumer {

    private final CatalogService catalog;

    public ReviewScoredConsumer(CatalogService catalog) {
        this.catalog = catalog;
    }

    @KafkaListener(topics = ReviewScoredEvent.TOPIC, groupId = "catalog-service")
    public void onReviewScored(ReviewScoredEvent event) {
        catalog.applySentiment(event.reviewId(), event.label(), event.score());
    }
}
