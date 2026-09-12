package com.shopflow.search.messaging;

import com.shopflow.common.event.ProductUpdatedEvent;
import com.shopflow.search.domain.ProductDocument;
import com.shopflow.search.repository.ProductSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Projects {@code product.events} into the Elasticsearch index. Idempotent:
 * the document id equals the productId, so reprocessing a duplicate event is a
 * harmless upsert (at-least-once delivery safe).
 */
@Component
public class ProductEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductEventConsumer.class);

    private final ProductSearchRepository repository;

    public ProductEventConsumer(ProductSearchRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = ProductUpdatedEvent.TOPIC, groupId = "search-service")
    public void onProductUpdated(ProductUpdatedEvent event) {
        log.debug("Indexing product {} (event {})", event.productId(), event.eventId());
        ProductDocument doc = new ProductDocument(
                event.productId(), event.name(), event.category(), event.price(), event.active());
        repository.save(doc);
    }
}
