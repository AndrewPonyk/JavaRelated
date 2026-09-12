package com.rtap.streaming.aggregation;

import com.fasterxml.jackson.databind.JsonNode;
import com.rtap.streaming.common.model.MetricAggregate;
import com.rtap.streaming.common.serde.Json;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Elasticsearch/OpenSearch bulk-upsert sink — the sub-second hot path.
 *
 * <p><b>Effectively-once contract (ARCHITECTURE.md §2.3):</b> delivery is
 * at-least-once — the buffer is flushed on every checkpoint barrier (Flink calls
 * {@link SinkWriter#flush} before the checkpoint completes), so data is durable in ES
 * before source offsets commit. Every document is indexed with the deterministic
 * {@link MetricAggregate#documentId()} as {@code _id}, so post-failure replays
 * overwrite identical documents instead of duplicating them.
 *
 * <p>Implementation note (ADR #8): a deliberate ~200-line HTTP client on the stable
 * {@code _bulk} API instead of the version-coupled Flink ES connectors — one artifact
 * works against both Elasticsearch 8 (local) and AWS OpenSearch, and the failure
 * semantics stay auditable: retry 429/5xx with backoff, exhaust ⇒ fail the checkpoint
 * (correctness over availability), 4xx mapping errors fail loudly.
 */
public class ElasticsearchBulkSink implements Sink<MetricAggregate>, Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchBulkSink.class);

    private final String baseUrl;          // e.g. http://elasticsearch:9200
    private final String indexPrefix;      // metrics-aggregates → index metrics-aggregates-<windowSize>
    private final String username;         // optional basic auth (AWS OpenSearch internal users / local none)
    private final String password;
    private final int maxBatch;
    private final int maxRetries;

    public ElasticsearchBulkSink(String baseUrl, String indexPrefix,
                                 String username, String password,
                                 int maxBatch, int maxRetries) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.indexPrefix = indexPrefix;
        this.username = username;
        this.password = password;
        this.maxBatch = maxBatch;
        this.maxRetries = maxRetries;
    }

    @Override
    public SinkWriter<MetricAggregate> createWriter(InitContext context) {
        return new BulkWriter();
    }

    /** Visible for tests — writers are independent of Flink runtime context. */
    BulkWriter newWriter() {
        return new BulkWriter();
    }

    class BulkWriter implements SinkWriter<MetricAggregate> {

        private final HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        private final List<MetricAggregate> buffer = new ArrayList<>();

        @Override
        public void write(MetricAggregate element, Context context) throws IOException {
            buffer.add(element);
            if (buffer.size() >= maxBatch) {
                flushBuffer();
            }
        }

        @Override
        public void flush(boolean endOfInput) throws IOException {
            flushBuffer();
        }

        @Override
        public void close() throws IOException {
            flushBuffer();
        }

        private void flushBuffer() throws IOException {
            if (buffer.isEmpty()) {
                return;
            }
            String body = buildBulkBody(buffer);
            int attempt = 0;
            while (true) {
                try {
                    HttpResponse<String> response = http.send(bulkRequest(body),
                            HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        failOnItemErrors(response.body());
                        buffer.clear();
                        return;
                    }
                    if (!isRetryable(response.statusCode())) {
                        throw new IOException("Elasticsearch bulk failed: HTTP " + response.statusCode()
                                + " " + snippet(response.body()));
                    }
                    LOG.warn("Elasticsearch bulk got HTTP {} (attempt {}/{})",
                            response.statusCode(), attempt + 1, maxRetries);
                } catch (RetryableBulkException e) {
                    LOG.warn("Elasticsearch bulk partially rejected: {} (attempt {}/{})",
                            e.getMessage(), attempt + 1, maxRetries);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted during Elasticsearch bulk", e);
                } catch (IOException e) {
                    if (attempt >= maxRetries) {
                        throw e; // connectivity exhausted → fail the checkpoint, replay after restart
                    }
                    LOG.warn("Elasticsearch bulk I/O error: {} (attempt {}/{})",
                            e.getMessage(), attempt + 1, maxRetries);
                }
                if (++attempt > maxRetries) {
                    throw new IOException("Elasticsearch bulk failed after " + maxRetries + " retries");
                }
                backoff(attempt);
            }
        }

        private HttpRequest bulkRequest(String ndjson) {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/_bulk"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-ndjson")
                    .POST(HttpRequest.BodyPublishers.ofString(ndjson, StandardCharsets.UTF_8));
            if (username != null && !username.isBlank()) {
                String token = Base64.getEncoder()
                        .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
                builder.header("Authorization", "Basic " + token);
            }
            return builder.build();
        }

        /** 429/5xx per-item statuses → retry the whole (idempotent) bulk; other 4xx → fatal. */
        private void failOnItemErrors(String responseBody) throws IOException, RetryableBulkException {
            JsonNode root = Json.MAPPER.readTree(responseBody);
            if (!root.path("errors").asBoolean(false)) {
                return;
            }
            boolean retryable = true;
            String firstError = null;
            for (JsonNode item : root.path("items")) {
                JsonNode action = item.elements().hasNext() ? item.elements().next() : item;
                int status = action.path("status").asInt(200);
                if (status >= 400) {
                    if (firstError == null) {
                        firstError = "status=" + status + " " + action.path("error").path("reason").asText("");
                    }
                    retryable &= isRetryable(status);
                }
            }
            if (retryable) {
                throw new RetryableBulkException(firstError);
            }
            throw new IOException("Elasticsearch rejected documents (non-retryable): " + firstError);
        }

        private void backoff(int attempt) throws IOException {
            try {
                Thread.sleep(Math.min(200L * (1L << Math.min(attempt, 6)), 10_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted during backoff", e);
            }
        }
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    static String buildBulkBody(List<MetricAggregate> batch) throws IOException {
        StringBuilder sb = new StringBuilder(batch.size() * 256);
        for (MetricAggregate agg : batch) {
            // The action line is built with Jackson, never string concatenation: the
            // document id embeds the producer-controlled metricKey, and a crafted key
            // must not be able to inject bulk-action JSON (e.g. redirect the _index).
            var action = Json.MAPPER.createObjectNode();
            var index = action.putObject("index");
            index.put("_index", indexNameFor(agg));
            index.put("_id", agg.documentId());
            sb.append(Json.MAPPER.writeValueAsString(action)).append('\n');
            sb.append(Json.MAPPER.writeValueAsString(agg)).append('\n');
        }
        return sb.toString();
    }

    private static String indexNameFor(MetricAggregate agg) {
        return "metrics-aggregates-" + agg.getWindowSize();
    }

    private static String snippet(String body) {
        return body == null ? "" : body.substring(0, Math.min(body.length(), 300));
    }

    private static final class RetryableBulkException extends Exception {
        RetryableBulkException(String message) {
            super(message);
        }
    }
}
