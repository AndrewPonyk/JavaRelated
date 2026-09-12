package com.ehrplatform.events.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic provisioning.
 *
 * <p>Topics are created with multiple partitions (parallelism + per-patient
 * ordering by key) and a replication factor suitable for MSK (≥ 3 in prod;
 * the broker clamps to the available brokers in single-node/dev setups).
 *
 * <p>Serialization is plain JSON over a {@code String} serde (the autoconfigured
 * {@code KafkaTemplate<String,String>} + {@code JsonDeserializer}), matching the
 * envelopes the gateway's outbox relay publishes. Switch to Avro + Schema
 * Registry in Phase 3 (P3-5) for safe schema evolution.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic patientEventsTopic() {
        return TopicBuilder.name(KafkaTopics.PATIENT_EVENTS)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic encounterEventsTopic() {
        return TopicBuilder.name(KafkaTopics.ENCOUNTER_EVENTS)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic observationEventsTopic() {
        return TopicBuilder.name(KafkaTopics.OBSERVATION_EVENTS)
                .partitions(6)
                .replicas(1)
                .build();
    }
}
