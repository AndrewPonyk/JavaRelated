package com.rtap.streaming.anomaly;

import com.rtap.streaming.common.config.JobParams;
import com.rtap.streaming.common.config.KafkaConfig;
import com.rtap.streaming.common.model.AnomalyAlert;
import com.rtap.streaming.common.model.MetricAggregate;
import com.rtap.streaming.common.model.ModelParams;
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
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Anomaly detection end to end: baseline aggregates → injected spike → an alert on
 * alerts.anomalies.v1 visible to a read_committed consumer. A model update is
 * broadcast first (smoke of the control-topic path; the seasonal math itself is
 * unit-tested in ModelParamsTest / EwmaZScoreDetectorTest).
 */
class AnomalyEndToEndIT {

    private static final long BASE = 1_700_000_000_000L;
    private static final String METRIC = "orders.completed";

    static KafkaContainer kafka;
    static JobClient jobClient;

    @BeforeAll
    static void setUp() throws Exception {
        Assumptions.assumeTrue(dockerAvailable(), "Docker unavailable — skipping anomaly E2E IT");
        kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"))
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1");
        kafka.start();
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic(KafkaConfig.TOPIC_METRIC_AGGREGATES, 1, (short) 1),
                    new NewTopic(KafkaConfig.TOPIC_ANOMALY_ALERTS, 1, (short) 1),
                    new NewTopic(KafkaConfig.TOPIC_MODEL_UPDATES, 1, (short) 1)
            )).all().get(30, TimeUnit.SECONDS);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (jobClient != null) {
            try {
                jobClient.cancel().get(30, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
        if (kafka != null) kafka.stop();
    }

    @Test
    void spikeOnTheAggregateStreamRaisesACommittedAlert() throws Exception {
        publishModel();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);
        AnomalyDetectionJob.buildPipeline(env, JobParams.of(Map.of(
                "kafka.bootstrap.servers", kafka.getBootstrapServers(),
                "checkpoint.interval.ms", "1000",
                "detector.alpha", "0.15",
                "detector.z.threshold", "4.0",
                "detector.warmup.windows", "10",
                "detector.cooldown.ms", "0")));
        jobClient = env.executeAsync("anomaly-e2e");

        produceBaselineThenSpike();

        List<AnomalyAlert> alerts = new ArrayList<>();
        await().atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            drainAlerts(alerts);
            assertThat(alerts).isNotEmpty();
        });

        AnomalyAlert alert = alerts.stream()
                .filter(a -> a.getObserved() == 2000.0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no alert for the injected spike, got: " + alerts));
        assertThat(alert.getMetricKey()).isEqualTo(METRIC);
        assertThat(Math.abs(alert.getScore())).isGreaterThan(alert.getThreshold());
        assertThat(alert.getSeverity()).isEqualTo(AnomalyAlert.SEVERITY_CRITICAL); // z >> 2×threshold
        assertThat(alert.getDetector()).isIn(EwmaZScoreDetector.DETECTOR_ONLINE, EwmaZScoreDetector.DETECTOR_SEASONAL);
        assertThat(alert.getModelVersion()).isIn("-", "v-e2e"); // broadcast arrival is racy by design
        assertThat(alert.getAlertId()).isNotBlank();
    }

    private static void publishModel() throws Exception {
        ModelParams model = new ModelParams();
        model.setMetricKey(METRIC);
        model.setModelVersion("v-e2e");
        model.setZThreshold(4.0); // no seasonal arrays → pure EWMA with model threshold
        try (KafkaProducer<byte[], byte[]> producer = producer()) {
            producer.send(new ProducerRecord<>(KafkaConfig.TOPIC_MODEL_UPDATES,
                    METRIC.getBytes(), Json.MAPPER.writeValueAsBytes(model)));
            producer.flush();
        }
    }

    private static void produceBaselineThenSpike() throws Exception {
        try (KafkaProducer<byte[], byte[]> producer = producer()) {
            for (int i = 0; i < 40; i++) {
                producer.send(new ProducerRecord<>(KafkaConfig.TOPIC_METRIC_AGGREGATES,
                        METRIC.getBytes(), Json.MAPPER.writeValueAsBytes(
                        aggregate(BASE + i * 1000L, 100.0 + (i % 5))))); // stable, small wobble
            }
            producer.send(new ProducerRecord<>(KafkaConfig.TOPIC_METRIC_AGGREGATES,
                    METRIC.getBytes(), Json.MAPPER.writeValueAsBytes(
                    aggregate(BASE + 40_000L, 2000.0)))); // the anomaly
            producer.flush();
        }
    }

    private static MetricAggregate aggregate(long windowStart, double sum) {
        MetricAggregate agg = new MetricAggregate();
        agg.setMetricKey(METRIC);
        agg.setWindowSize("1s");
        agg.setWindowStart(windowStart);
        agg.setWindowEnd(windowStart + 1000);
        agg.setCount(Math.round(sum / 10));
        agg.setSum(sum);
        agg.setMin(1.0);
        agg.setMax(50.0);
        return agg;
    }

    private static KafkaProducer<byte[], byte[]> producer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaProducer<>(props);
    }

    private static void drainAlerts(List<AnomalyAlert> sink) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "alerts-drain-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed"); // exactly-once at the last hop
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        sink.clear();
        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(KafkaConfig.TOPIC_ANOMALY_ALERTS));
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    try {
                        sink.add(Json.MAPPER.readValue(record.value(), AnomalyAlert.class));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }
}
