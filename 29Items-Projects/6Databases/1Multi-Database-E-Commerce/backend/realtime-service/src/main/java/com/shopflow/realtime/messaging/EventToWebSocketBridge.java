package com.shopflow.realtime.messaging;

import com.shopflow.common.event.OrderPlacedEvent;
import com.shopflow.common.event.ProductUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Forwards Kafka domain events to subscribed WebSocket clients. A customer's
 * browser subscribes to {@code /topic/orders/{customerId}} for live order
 * updates; a product page subscribes to {@code /topic/products/{productId}} for
 * price/stock changes. No polling required.
 */
@Component
public class EventToWebSocketBridge {

    private static final Logger log = LoggerFactory.getLogger(EventToWebSocketBridge.class);

    private final SimpMessagingTemplate messaging;

    public EventToWebSocketBridge(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @KafkaListener(topics = OrderPlacedEvent.TOPIC, groupId = "realtime-service")
    public void onOrderPlaced(OrderPlacedEvent event) {
        String destination = "/topic/orders/" + event.customerId();
        log.debug("Pushing order {} update to {}", event.orderId(), destination);
        messaging.convertAndSend(destination, event);
    }

    @KafkaListener(topics = ProductUpdatedEvent.TOPIC, groupId = "realtime-service")
    public void onProductUpdated(ProductUpdatedEvent event) {
        String destination = "/topic/products/" + event.productId();
        log.debug("Pushing product {} update to {}", event.productId(), destination);
        messaging.convertAndSend(destination, event);
    }
}
