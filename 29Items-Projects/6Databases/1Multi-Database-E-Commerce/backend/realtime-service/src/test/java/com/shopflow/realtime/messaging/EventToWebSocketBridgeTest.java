package com.shopflow.realtime.messaging;

import static org.mockito.Mockito.verify;

import com.shopflow.common.event.OrderPlacedEvent;
import com.shopflow.common.event.ProductUpdatedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class EventToWebSocketBridgeTest {

    @Mock
    private SimpMessagingTemplate messaging;

    @Test
    void orderEvent_pushedToCustomerTopic() {
        var bridge = new EventToWebSocketBridge(messaging);
        var event = new OrderPlacedEvent(UUID.randomUUID(), null, 1, Instant.now(),
                "order-1", "cust-9", new BigDecimal("5.00"), "EUR",
                List.of(new OrderPlacedEvent.Line("p1", 1, new BigDecimal("5.00"))));

        bridge.onOrderPlaced(event);

        verify(messaging).convertAndSend("/topic/orders/cust-9", event);
    }

    @Test
    void productEvent_pushedToProductTopic() {
        var bridge = new EventToWebSocketBridge(messaging);
        var event = new ProductUpdatedEvent(UUID.randomUUID(), null, 1, Instant.now(),
                "prod-7", "Thing", "things", new BigDecimal("9.99"), true);

        bridge.onProductUpdated(event);

        verify(messaging).convertAndSend("/topic/products/prod-7", event);
    }
}
