package com.example.inventory.outbox;

import com.example.inventory.messaging.StockChangedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final String stockTopic;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper,
                         @Value("${app.kafka.stock-changed-topic}") String stockTopic) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.stockTopic = stockTopic;
    }

    public void append(StockChangedEvent event) {
        try {
            repository.save(new OutboxEvent(event.eventId(), "InventoryItem", event.inventoryItemId(),
                    event.eventType(), stockTopic, event.inventoryItemId().toString(),
                    objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize stock event", exception);
        }
    }
}

