package com.rtap.api;

import com.rtap.api.stream.SseBroadcaster;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Full-stack API integration test: real PostgreSQL (Testcontainers) with the real
 * Flyway migrations from /migrations, embedded Kafka for the ingest listeners, and
 * the SSE fan-out path. Covers: definitions CRUD + conflict, aggregate queries with
 * query-time re-bucketing, alert ingest → history → acknowledge workflow, SSE handshake.
 *
 * Skips (class-level) when Docker is unavailable so `mvn verify` stays green.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = {"metrics.aggregates.v1", "alerts.anomalies.v1"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIf(value = "dockerAvailable", disabledReason = "Docker unavailable")
class AnalyticsApiIT {

    static PostgreSQLContainer<?> postgres;

    static boolean dockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @BeforeAll
    static void startPostgres() {
        // started lazily inside the condition-guarded class
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "filesystem:../../migrations");
        registry.add("spring.kafka.bootstrap-servers",
                () -> System.getProperty("spring.embedded.kafka.brokers"));
        registry.add("spring.kafka.producer.key-serializer",
                () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer",
                () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("rtap.elasticsearch.enabled", () -> "false"); // PG path under test
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Autowired TestRestTemplate rest;
    @Autowired JdbcClient jdbc;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired SseBroadcaster broadcaster;
    @LocalServerPort int port;

    // ── 1) metric definitions: seeded + CRUD + conflict ─────────────────────

    @Test
    @Order(1)
    void definitionsSeededByMigrationsAndCreatable() {
        ResponseEntity<String> list = rest.getForEntity("/api/v1/metrics", String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("orders.completed", "payments.captured", "users.signup");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"metricKey":"carts.abandoned","displayName":"Carts abandoned","unit":"count"}
                """;
        ResponseEntity<String> created =
                rest.postForEntity("/api/v1/metrics", new HttpEntity<>(body, headers), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getFirst("Location")).isEqualTo("/api/v1/metrics/carts.abandoned");

        ResponseEntity<String> conflict =
                rest.postForEntity("/api/v1/metrics", new HttpEntity<>(body, headers), String.class);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> invalid = rest.postForEntity("/api/v1/metrics",
                new HttpEntity<>("{\"metricKey\":\"NOT VALID\",\"displayName\":\"x\"}", headers), String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody()).contains("metricKey");
    }

    // ── 2) aggregate queries: merge dimensions + re-bucket coarser windows ──

    @Test
    @Order(2)
    void aggregatesQueryMergesSeriesAndRebuckets() {
        Instant base = Instant.parse("2026-07-01T10:00:00Z");
        insertAggregate(base, "eu", 3, 30, 5, 15);
        insertAggregate(base, "us", 2, 40, 10, 30);              // same window, other series
        insertAggregate(base.plusSeconds(60), "eu", 1, 10, 10, 10);

        String url = "/api/v1/metrics/orders.completed/aggregates?window=1m&from=%s&to=%s"
                .formatted(base, base.plusSeconds(300));
        ResponseEntity<String> oneMinute = rest.getForEntity(url, String.class);
        assertThat(oneMinute.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(oneMinute.getBody()).contains("\"count\":5");   // eu+us merged in window 1
        assertThat(oneMinute.getBody()).contains("\"sum\":70.0");

        String fiveMinuteUrl = "/api/v1/metrics/orders.completed/aggregates?window=5m&from=%s&to=%s"
                .formatted(base, base.plusSeconds(300));
        ResponseEntity<String> fiveMinutes = rest.getForEntity(fiveMinuteUrl, String.class);
        assertThat(fiveMinutes.getBody()).contains("\"count\":6"); // both windows re-bucketed together
        assertThat(fiveMinutes.getBody()).contains("\"sum\":80.0");

        ResponseEntity<String> unknown =
                rest.getForEntity("/api/v1/metrics/no.such.metric/aggregates", String.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> badWindow =
                rest.getForEntity("/api/v1/metrics/orders.completed/aggregates?window=42d", String.class);
        assertThat(badWindow.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void insertAggregate(Instant windowStart, String region, long count, double sum,
                                 double min, double max) {
        jdbc.sql("""
                        INSERT INTO metric_aggregates
                          (metric_key, window_size, window_start, window_end, dimensions_hash, dimensions,
                           event_count, value_sum, value_min, value_max)
                        VALUES ('orders.completed', '1m', :ws, :we, :dh, CAST(:dims AS jsonb), :c, :s, :mn, :mx)
                        ON CONFLICT DO NOTHING
                        """)
                .param("ws", Timestamp.from(windowStart))
                .param("we", Timestamp.from(windowStart.plusSeconds(60)))
                .param("dh", region)
                .param("dims", "{\"region\":\"" + region + "\"}")
                .param("c", count).param("s", sum).param("mn", min).param("mx", max)
                .update();
    }

    // ── 3) alert ingest (Kafka → PG) + acknowledge workflow ─────────────────

    @Test
    @Order(3)
    void alertIngestAndAcknowledgeWorkflow() {
        String alertId = UUID.randomUUID().toString();
        String alertJson = """
                {"alertId":"%s","metricKey":"orders.completed","detector":"ewma-zscore",
                 "modelVersion":"-","score":6.3,"threshold":4.0,"observed":182,"expected":46.5,
                 "severity":"critical","windowStart":1700000000000,"windowEnd":1700000001000,
                 "detectedAt":1700000001200,"dimensions":{"region":"eu"}}
                """.formatted(alertId);

        kafka.send("alerts.anomalies.v1", alertId, alertJson);
        kafka.send("alerts.anomalies.v1", alertId, alertJson); // redelivery — must stay one row

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            ResponseEntity<String> open = rest.getForEntity("/api/v1/alerts?status=open", String.class);
            assertThat(open.getBody()).contains(alertId);
        });
        Long rows = jdbc.sql("SELECT count(*) FROM anomaly_alerts WHERE alert_id = CAST(:id AS uuid)")
                .param("id", alertId).query(Long.class).single();
        assertThat(rows).isEqualTo(1); // idempotent ingest

        ResponseEntity<String> acked =
                rest.postForEntity("/api/v1/alerts/" + alertId + "/ack", null, String.class);
        assertThat(acked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(acked.getBody()).contains("\"status\":\"acknowledged\"");

        ResponseEntity<String> ackedAgain =
                rest.postForEntity("/api/v1/alerts/" + alertId + "/ack", null, String.class);
        assertThat(ackedAgain.getStatusCode()).isEqualTo(HttpStatus.OK); // idempotent

        ResponseEntity<String> unknown = rest.postForEntity(
                "/api/v1/alerts/" + UUID.randomUUID() + "/ack", null, String.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── 4) SSE: handshake + broadcast reaches the wire ───────────────────────

    @Test
    @Order(4)
    void sseHandshakeAndBroadcast() throws Exception {
        URI uri = URI.create("http://localhost:" + port + "/api/v1/stream/metrics");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "text/event-stream");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

            Future<String> handshake = executor.submit(() -> readUntilEvent(reader, "connected"));
            assertThat(handshake.get()).contains("event:connected");

            Future<String> live = executor.submit(() -> readUntilEvent(reader, "aggregate"));
            await().atMost(Duration.ofSeconds(10)).until(() -> broadcaster.activeSubscribers() > 0);
            broadcaster.broadcast("aggregate", "{\"metricKey\":\"orders.completed\"}");
            assertThat(live.get()).contains("event:aggregate");
        } finally {
            executor.shutdownNow();
            connection.disconnect();
        }
    }

    private static String readUntilEvent(BufferedReader reader, String eventName) throws Exception {
        String line;
        StringBuilder seen = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            seen.append(line).append('\n');
            if (line.replace(" ", "").equals("event:" + eventName)) {
                return line.replace(" ", "");
            }
        }
        throw new AssertionError("Stream ended before event '%s'; saw:%n%s".formatted(eventName, seen));
    }
}
