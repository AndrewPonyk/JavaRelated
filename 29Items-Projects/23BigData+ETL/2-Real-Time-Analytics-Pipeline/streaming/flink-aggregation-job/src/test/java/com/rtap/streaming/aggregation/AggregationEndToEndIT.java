package com.rtap.streaming.aggregation;

import com.rtap.streaming.common.config.JobParams;
import com.rtap.streaming.common.config.KafkaConfig;
import com.rtap.streaming.common.model.BusinessEvent;
import com.rtap.streaming.common.model.DeadLetter;
import com.rtap.streaming.common.model.MetricAggregate;
import com.rtap.streaming.common.serde.Json;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The exactly-once walking skeleton, end to end: real Kafka (transactions!), real
 * PostgreSQL (Flyway schema from /migrations), the real job dataflow.
 *
 * Verifies:
 *  - windowed aggregates land on metrics.aggregates.v1 and are visible to a
 *    read_committed consumer (transactional sink actually commits);
 *  - the PostgreSQL upsert sink materializes the same aggregates;
 *  - poison pills are dead-lettered with a replayable envelope, never dropped.
 *
 * Self-skips when Docker is unavailable so `mvn verify` stays green everywhere.
 */
class AggregationEndToEndIT {

    private static final long BASE = 1_700_000_000_000L;

    static KafkaContainer kafka;
    static PostgreSQLContainer<?> postgres;
    static JobClient jobClient;

    @BeforeAll
    static void setUp() throws Exception {
        Assumptions.assumeTrue(dockerAvailable(), "Docker unavailable — skipping E2E IT");

        kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"))
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1");
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
        kafka.start();
        postgres.start();

        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic(KafkaConfig.TOPIC_EVENTS_RAW, 1, (short) 1),
                    new NewTopic(KafkaConfig.TOPIC_METRIC_AGGREGATES, 1, (short) 1),
                    new NewTopic(KafkaConfig.TOPIC_EVENTS_DLQ, 1, (short) 1),
                    new NewTopic(KafkaConfig.TOPIC_EVENTS_LATE, 1, (short) 1)
            )).all().get(30, TimeUnit.SECONDS);
        }
        applyMigrations();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (jobClient != null) {
            try {
                jobClient.cancel().get(30, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // job may already be gone
            }
        }
        if (postgres != null) postgres.stop();
        if (kafka != null) kafka.stop();
    }

    @Test
    void exactlyOnceSkeleton_kafkaToAggregatesToPostgresWithDlq() throws Exception {
        produceInput();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);
        MetricsAggregationJob.buildPipeline(env, JobParams.of(Map.of(
                "kafka.bootstrap.servers", kafka.getBootstrapServers(),
                "checkpoint.interval.ms", "1000",
                "checkpoint.min.pause.ms", "200",
                "enable-pg-sink", "true",
                "postgres.url", postgres.getJdbcUrl(),
                "postgres.user", postgres.getUsername(),
                "postgres.password", postgres.getPassword(),
                "postgres.batch.size", "10")));
        jobClient = env.executeAsync("aggregation-e2e");

        // ── 1) transactional aggregates visible to read_committed consumers ──
        List<MetricAggregate> aggregates = new ArrayList<>();
        await().atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            drain(KafkaConfig.TOPIC_METRIC_AGGREGATES, aggregates, MetricAggregate.class);
            assertThat(firstWindowAggregates(aggregates, "eu")).isNotEmpty();
            assertThat(firstWindowAggregates(aggregates, "us")).isNotEmpty();
        });

        MetricAggregate eu = firstWindowAggregates(aggregates, "eu").get(0);
        assertThat(eu.getCount()).isEqualTo(3);
        assertThat(eu.getSum()).isEqualTo(35.0);
        assertThat(eu.getMin()).isEqualTo(5.0);
        assertThat(eu.getMax()).isEqualTo(20.0);
        MetricAggregate us = firstWindowAggregates(aggregates, "us").get(0);
        assertThat(us.getCount()).isEqualTo(1);
        assertThat(us.getSum()).isEqualTo(100.0);

        // ── 2) poison pills → DLQ with replayable envelope ───────────────────
        List<DeadLetter> deadLetters = new ArrayList<>();
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            drain(KafkaConfig.TOPIC_EVENTS_DLQ, deadLetters, DeadLetter.class);
            assertThat(deadLetters).hasSizeGreaterThanOrEqualTo(2);
        });
        assertThat(deadLetters).anySatisfy(dl -> {
            assertThat(dl.getError()).contains("malformed JSON");
            assertThat(new String(dl.decodePayload(), StandardCharsets.UTF_8)).isEqualTo("definitely{{not-json");
        });
        assertThat(deadLetters).anySatisfy(dl -> assertThat(dl.getError()).contains("missing eventType"));

        // ── 3) PostgreSQL history materialized via idempotent upsert ─────────
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            try (Connection c = pgConnection(); Statement s = c.createStatement()) {
                ResultSet rs = s.executeQuery("""
                        SELECT event_count, value_sum FROM metric_aggregates
                        WHERE metric_key = 'orders.completed' AND window_size = '1s'
                          AND dimensions ->> 'region' = 'eu'
                          AND window_start = to_timestamp(%d / 1000.0)
                        """.formatted(BASE));
                assertThat(rs.next()).as("eu 1s row present in PostgreSQL").isTrue();
                assertThat(rs.getLong(1)).isEqualTo(3);
                assertThat(rs.getDouble(2)).isEqualTo(35.0);
            }
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static void produceInput() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        try (KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(props)) {
            send(producer, validEvent("eu", BASE + 100, 10.0));
            send(producer, validEvent("eu", BASE + 400, 20.0));
            send(producer, validEvent("us", BASE + 500, 100.0));
            send(producer, validEvent("eu", BASE + 900, 5.0));
            send(producer, "definitely{{not-json".getBytes(StandardCharsets.UTF_8));
            send(producer, "{\"eventId\":\"x\",\"occurredAt\":1,\"value\":2}".getBytes(StandardCharsets.UTF_8));
            // watermark advancer: pushes event time far past all asserted windows (1s AND 1m)
            send(producer, validEvent("eu", BASE + 180_000, 1.0));
            producer.flush();
        }
    }

    private static void send(KafkaProducer<byte[], byte[]> producer, byte[] payload) {
        producer.send(new ProducerRecord<>(KafkaConfig.TOPIC_EVENTS_RAW, null, payload));
    }

    private static byte[] validEvent(String region, long occurredAt, double value) {
        try {
            BusinessEvent e = new BusinessEvent();
            e.setEventId(UUID.randomUUID().toString());
            e.setEventType("orders.completed");
            e.setSource("it");
            e.setOccurredAt(occurredAt);
            e.setValue(value);
            e.setDimensions(Map.of("region", region));
            return Json.MAPPER.writeValueAsBytes(e);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static List<MetricAggregate> firstWindowAggregates(List<MetricAggregate> all, String region) {
        return all.stream()
                .filter(a -> "1s".equals(a.getWindowSize())
                        && a.getWindowStart() == BASE
                        && region.equals(a.getDimensions().get("region")))
                .toList();
    }

    /** Drains all currently-committed records of a topic into {@code sink} (read_committed). */
    private static <T> void drain(String topic, List<T> sink, Class<T> type) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "drain-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        sink.clear();
        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    try {
                        sink.add(Json.MAPPER.readValue(record.value(), type));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    private static void applyMigrations() throws Exception {
        Path migrations = Path.of("..", "..", "migrations");
        try (Connection c = pgConnection(); Statement s = c.createStatement();
             Stream<Path> files = Files.list(migrations)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
                s.execute(Files.readString(file));
            }
        }
    }

    private static Connection pgConnection() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }
}
