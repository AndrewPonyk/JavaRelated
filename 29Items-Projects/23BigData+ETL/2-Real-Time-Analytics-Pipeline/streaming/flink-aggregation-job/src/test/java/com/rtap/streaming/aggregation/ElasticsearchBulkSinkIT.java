package com.rtap.streaming.aggregation;

import com.fasterxml.jackson.databind.JsonNode;
import com.rtap.streaming.common.model.MetricAggregate;
import com.rtap.streaming.common.serde.Json;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The effectively-once contract of the hot path, against a real Elasticsearch 8:
 * deterministic {@code _id} upserts mean a replay overwrites — never duplicates.
 * Uses the real index template from elasticsearch/index-templates (flattened dims).
 */
class ElasticsearchBulkSinkIT {

    static ElasticsearchContainer elasticsearch;
    static String baseUrl;
    static final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void setUp() throws Exception {
        Assumptions.assumeTrue(dockerAvailable(), "Docker unavailable — skipping ES sink IT");
        elasticsearch = new ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.15.2"))
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
        elasticsearch.start();
        baseUrl = "http://" + elasticsearch.getHttpHostAddress();

        // install the real index template shipped with the repo
        String template = Files.readString(Path.of("..", "..", "elasticsearch", "index-templates",
                "metrics-aggregates.json"));
        HttpResponse<String> response = http.send(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/_index_template/metrics-aggregates"))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(template))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    @AfterAll
    static void tearDown() {
        if (elasticsearch != null) elasticsearch.stop();
    }

    @Test
    void upsertsAreIdempotentAcrossReplays() throws Exception {
        ElasticsearchBulkSink sink = new ElasticsearchBulkSink(baseUrl, "metrics-aggregates",
                "", "", 100, 3);

        try (var writer = sink.newWriter()) {
            writer.write(aggregate("orders.completed", 1_700_000_000_000L, 3, 35.0), null);
            writer.write(aggregate("users.signup", 1_700_000_000_000L, 1, 1.0), null);
            writer.flush(false);

            // simulate a post-failure replay of the same window with the final value
            writer.write(aggregate("orders.completed", 1_700_000_000_000L, 4, 42.0), null);
            writer.flush(false);
        }

        http.send(HttpRequest.newBuilder().uri(URI.create(baseUrl + "/metrics-aggregates-1s/_refresh"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());

        // replay overwrote — still exactly 2 documents
        JsonNode count = getJson("/metrics-aggregates-1s/_count");
        assertThat(count.path("count").asLong()).isEqualTo(2);

        String docId = aggregate("orders.completed", 1_700_000_000_000L, 4, 42.0).documentId();
        JsonNode doc = getJson("/metrics-aggregates-1s/_doc/" + java.net.URLEncoder.encode(docId, java.nio.charset.StandardCharsets.UTF_8));
        assertThat(doc.path("found").asBoolean()).isTrue();
        assertThat(doc.path("_source").path("sum").asDouble()).isEqualTo(42.0);
        assertThat(doc.path("_source").path("count").asLong()).isEqualTo(4);
        assertThat(doc.path("_source").path("dimensions").path("region").asText()).isEqualTo("eu");
    }

    private static MetricAggregate aggregate(String key, long windowStart, long count, double sum) {
        MetricAggregate agg = new MetricAggregate();
        agg.setMetricKey(key);
        agg.setWindowSize("1s");
        agg.setWindowStart(windowStart);
        agg.setWindowEnd(windowStart + 1000);
        agg.setCount(count);
        agg.setSum(sum);
        agg.setMin(1.0);
        agg.setMax(20.0);
        agg.setDimensions(Map.of("region", "eu"));
        return agg;
    }

    private static JsonNode getJson(String path) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        return Json.MAPPER.readTree(response.body());
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }
}
