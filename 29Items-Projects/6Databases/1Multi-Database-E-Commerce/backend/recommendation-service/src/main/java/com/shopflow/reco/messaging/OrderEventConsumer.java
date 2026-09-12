package com.shopflow.reco.messaging;

import com.shopflow.common.event.OrderPlacedEvent;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code order.events} and grows the purchase graph: for each line it
 * MERGEs the customer + product nodes and a {@code BOUGHT} edge. MERGE makes the
 * write idempotent, so duplicate event delivery does not distort weights.
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private static final String UPSERT_BOUGHT = """
            MERGE (c:Customer {id: $customerId})
            MERGE (p:Product {id: $productId})
            MERGE (c)-[r:BOUGHT]->(p)
              ON CREATE SET r.count = $qty, r.firstAt = timestamp()
              ON MATCH  SET r.count = r.count + $qty
            """;

    private final Driver neo4jDriver;

    public OrderEventConsumer(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    @KafkaListener(topics = OrderPlacedEvent.TOPIC, groupId = "recommendation-service")
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.debug("Updating purchase graph for order {} ({} lines)",
                event.orderId(), event.lines().size());
        try (var session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                for (OrderPlacedEvent.Line line : event.lines()) {
                    tx.run(UPSERT_BOUGHT, Values.parameters(
                            "customerId", event.customerId(),
                            "productId", line.productId(),
                            "qty", line.quantity()));
                }
                return null;
            });
        }
    }
}
