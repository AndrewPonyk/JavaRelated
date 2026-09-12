package com.shopflow.order.messaging;

import com.shopflow.common.event.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes order domain events to Kafka.
 *
 * <p>Events are keyed by {@code orderId} so all events for one order land on the
 * same partition and preserve ordering. In production this is fed by a
 * <strong>transactional outbox</strong> relay (events written in the same Oracle
 * transaction as the order, then shipped by a poller / Debezium) to guarantee
 * at-least-once delivery without dual-write loss. The service layer here invokes
 * it from an after-commit hook as a pragmatic stand-in for that relay.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(OrderPlacedEvent event) {
        log.info("Publishing {} for order {} (eventId={})",
                OrderPlacedEvent.TYPE, event.orderId(), event.eventId());
        kafkaTemplate.send(OrderPlacedEvent.TOPIC, event.orderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // Outbox relay would retry; log so it is observable.
                        log.error("Failed to publish OrderPlacedEvent for order {}", event.orderId(), ex);
                    }
                });
    }
}
