package com.healthcare.claims.integration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * TestContainers resource for integration tests.
 * Manages PostgreSQL, Kafka, Elasticsearch, and Redis containers.
 */
public class TestContainersResource implements QuarkusTestResourceLifecycleManager {

    private static PostgreSQLContainer<?> postgres;
    private static KafkaContainer kafka;
    private static GenericContainer<?> elasticsearch;
    private static GenericContainer<?> redis;

    @Override
    public Map<String, String> start() {
        Map<String, String> config = new HashMap<>();

        // PostgreSQL
        postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("claims_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);
        postgres.start();

        // Disable DevServices to use TestContainers instead
        config.put("quarkus.datasource.devservices.enabled", "false");
        config.put("quarkus.datasource.reactive.url",
            "postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/claims_test");
        config.put("quarkus.datasource.username", "test");
        config.put("quarkus.datasource.password", "test");
        config.put("quarkus.flyway.migrate-at-start", "false");
        config.put("quarkus.hibernate-orm.database.generation", "drop-and-create");

        // Kafka
        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
            .withReuse(true);
        kafka.start();

        config.put("kafka.bootstrap.servers", kafka.getBootstrapServers());

        // Configure Kafka channels for integration tests with real Kafka
        config.put("mp.messaging.outgoing.claim-events-out.connector", "smallrye-kafka");
        config.put("mp.messaging.outgoing.claim-events-out.topic", "claim-events");
        config.put("mp.messaging.outgoing.claim-events-out.key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        config.put("mp.messaging.outgoing.claim-events-out.value.serializer", "io.quarkus.kafka.client.serialization.JsonbSerializer");

        config.put("mp.messaging.outgoing.fraud-events-out.connector", "smallrye-kafka");
        config.put("mp.messaging.outgoing.fraud-events-out.topic", "fraud-events");
        config.put("mp.messaging.outgoing.fraud-events-out.key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        config.put("mp.messaging.outgoing.fraud-events-out.value.serializer", "io.quarkus.kafka.client.serialization.JsonbSerializer");

        config.put("mp.messaging.incoming.claim-events-in.connector", "smallrye-kafka");
        config.put("mp.messaging.incoming.claim-events-in.topic", "claim-events");
        config.put("mp.messaging.incoming.claim-events-in.auto.offset.reset", "earliest");
        config.put("mp.messaging.incoming.claim-events-in.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("mp.messaging.incoming.claim-events-in.value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        config.put("mp.messaging.incoming.fraud-alerts-in.connector", "smallrye-kafka");
        config.put("mp.messaging.incoming.fraud-alerts-in.topic", "fraud-alerts");
        config.put("mp.messaging.incoming.fraud-alerts-in.auto.offset.reset", "earliest");
        config.put("mp.messaging.incoming.fraud-alerts-in.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("mp.messaging.incoming.fraud-alerts-in.value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        // Elasticsearch
        elasticsearch = new GenericContainer<>(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.11.0"))
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
            .withExposedPorts(9200)
            .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(2)))
            .withReuse(true);
        elasticsearch.start();

        config.put("quarkus.elasticsearch.devservices.enabled", "false");
        config.put("quarkus.elasticsearch.hosts",
            elasticsearch.getHost() + ":" + elasticsearch.getMappedPort(9200));

        // Redis
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1))
            .withReuse(true);
        redis.start();

        config.put("quarkus.redis.devservices.enabled", "false");
        config.put("quarkus.redis.hosts", "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));

        // Disable seed data in tests
        config.put("claims.seed-data.enabled", "false");

        return config;
    }

    @Override
    public void stop() {
        // Containers with reuse=true are managed by TestContainers
        // They will be stopped when the JVM exits
    }
}
