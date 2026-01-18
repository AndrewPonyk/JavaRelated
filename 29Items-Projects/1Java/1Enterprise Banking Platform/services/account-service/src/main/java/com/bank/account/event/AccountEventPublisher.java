package com.bank.account.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Publisher for account domain events to Kafka.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventPublisher {

    private static final String TOPIC = "account-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Publish an account event to Kafka.
     *
     * @param event the event to publish
     * @return Mono signaling completion
     */
    public Mono<Void> publish(AccountEvent event) {
        return Mono.fromCallable(() -> {
                try {
                    return objectMapper.writeValueAsString(event);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to serialize event", e);
                }
            })
            .flatMap(json -> {
                log.info("Publishing event: type={}, aggregateId={}",
                    event.getEventType(), event.getAggregateId());

                return Mono.fromFuture(
                    kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), json)
                        .toCompletableFuture()
                );
            })
            .doOnSuccess(result -> log.debug("Event published successfully"))
            .doOnError(error -> log.error("Failed to publish event", error))
            .then();
    }
}
