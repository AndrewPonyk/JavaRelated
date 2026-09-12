package com.shopflow.reco.messaging;

import com.shopflow.common.event.ProductUpdatedEvent;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Values;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Keeps product nodes in the graph enriched with their display name by consuming
 * {@code product.events}. MERGE makes it idempotent and safe to run before or
 * after the corresponding BOUGHT edges are created.
 */
@Component
public class ProductGraphConsumer {

    private static final String UPSERT_PRODUCT = """
            MERGE (p:Product {id: $productId})
            SET p.name = $name, p.category = $category
            """;

    private final Driver neo4jDriver;

    public ProductGraphConsumer(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    @KafkaListener(topics = ProductUpdatedEvent.TOPIC, groupId = "recommendation-service")
    public void onProductUpdated(ProductUpdatedEvent event) {
        try (var session = neo4jDriver.session()) {
            session.executeWriteWithoutResult(tx -> tx.run(UPSERT_PRODUCT, Values.parameters(
                    "productId", event.productId(),
                    "name", event.name(),
                    "category", event.category())));
        }
    }
}
