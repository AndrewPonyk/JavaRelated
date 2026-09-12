package com.example.inventory.alert;

import com.example.inventory.inventory.domain.InventoryItem;
import com.example.inventory.inventory.repository.InventoryItemRepository;
import com.example.inventory.messaging.StockChangedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LowStockAlertConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(LowStockAlertConsumer.class);
    private static final String CONSUMER = "low-stock-alerts-v1";
    private final InboxEventRepository inboxRepository;
    private final LowStockAlertRepository alertRepository;
    private final InventoryItemRepository itemRepository;
    private final ObjectMapper objectMapper;

    public LowStockAlertConsumer(InboxEventRepository inboxRepository,
                                 LowStockAlertRepository alertRepository,
                                 InventoryItemRepository itemRepository,
                                 ObjectMapper objectMapper) {
        this.inboxRepository = inboxRepository;
        this.alertRepository = alertRepository;
        this.itemRepository = itemRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${app.kafka.stock-changed-topic}",
            groupId = "${app.kafka.low-stock-group:inventory-low-stock-alerts}")
    @Transactional
    public void consume(String payload) throws JsonProcessingException {
        StockChangedEvent event = objectMapper.readValue(payload, StockChangedEvent.class);
        InboxEventId inboxId = new InboxEventId(event.eventId(), CONSUMER);
        if (inboxRepository.existsById(inboxId)) {
            return;
        }
        InventoryItem item = itemRepository.findById(event.inventoryItemId()).orElse(null);
        if (item == null) {
            LOGGER.warn("Ignoring stock event {} for missing item {}", event.eventId(), event.inventoryItemId());
            inboxRepository.save(new InboxEvent(inboxId));
            return;
        }
        var open = alertRepository.findFirstByItemIdAndStatusOrderByCreatedAtDesc(
                item.getId(), AlertStatus.OPEN);
        if (event.lowStock() && open.isEmpty()) {
            alertRepository.save(new LowStockAlert(event.eventId(), item, event.availableQuantity(),
                    event.reorderPoint()));
        } else if (!event.lowStock()) {
            open.ifPresent(LowStockAlert::resolve);
        }
        inboxRepository.save(new InboxEvent(inboxId));
    }
}

