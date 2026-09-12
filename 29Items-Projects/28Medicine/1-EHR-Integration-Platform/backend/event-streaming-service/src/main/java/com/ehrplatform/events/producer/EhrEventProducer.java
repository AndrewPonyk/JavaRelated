package com.ehrplatform.events.producer;

import com.ehrplatform.common.dto.EhrEventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link EhrEventEnvelope} messages to Kafka as plain JSON.
 *
 * <p>Keyed by {@code resourceId} so all events for a resource land on the same
 * partition (ordering). Uses the same wire format as the gateway's outbox relay
 * (raw JSON via a String serde) so any consumer can read either source.
 */
@Component
public class EhrEventProducer {

    private static final Logger log = LoggerFactory.getLogger(EhrEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EhrEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(String topic, EhrEventEnvelope event) {
        try {
            kafkaTemplate.send(topic, event.resourceId(), objectMapper.writeValueAsString(event))
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish event {} to {}", event.eventId(), topic, ex);
                        } else {
                            log.debug("Published event {} to {}", event.eventId(), topic);
                        }
                    });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize event " + event.eventId(), e);
        }
    }
}
