package com.example.inventory.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);
    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay:500}")
    @Transactional
    public void publishBatch() {
        List<OutboxEvent> events = repository.lockNextBatch(PageRequest.of(0, 50));
        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload())
                        .get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
                event.markPublished(Instant.now());
            } catch (Exception exception) {
                event.markFailed(exception.getMessage());
                LOGGER.warn("Outbox publication failed for event {} attempt {}", event.getId(),
                        event.getAttempts(), exception);
                break;
            }
        }
    }
}
