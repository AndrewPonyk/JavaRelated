package com.example.pipeline.infrastructure.http;

import com.example.pipeline.application.filter.ThresholdPredicate;
import com.example.pipeline.application.port.MessageChannel;
import com.example.pipeline.application.port.MetricsRecorder;
import com.example.pipeline.domain.AggregateResult;
import com.example.pipeline.domain.AggregateSnapshot;
import com.example.pipeline.domain.MetricsSnapshot;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The pipeline's only HTTP endpoint — a small read/tune control plane.
 *
 * <table>
 *   <caption>Routes</caption>
 *   <tr><th>Method</th><th>Path</th><th>Purpose</th></tr>
 *   <tr><td>GET</td><td>/health</td><td>liveness plus queue depths</td></tr>
 *   <tr><td>GET</td><td>/metrics</td><td>counter snapshot</td></tr>
 *   <tr><td>GET</td><td>/aggregates</td><td>current per-sensor aggregates</td></tr>
 *   <tr><td>POST</td><td>/threshold</td><td>retune the filter live: {@code {"threshold": 60.0}}</td></tr>
 * </table>
 *
 * <p><strong>Security, and why it is proportionate</strong> (see
 * {@code docs/ARCHITECTURE.md} §2.5). This is a local diagnostic surface, not a public
 * API, but {@code POST /threshold} mutates a running pipeline, so it is not
 * unauthenticated:
 * <ul>
 *   <li>the server binds {@code 127.0.0.1} only — see {@link ControlPlaneServer};</li>
 *   <li>every request must carry {@code X-Pipeline-Token}, compared with
 *       {@link MessageDigest#isEqual} rather than {@code String.equals} so the comparison
 *       does not short-circuit on the first wrong byte;</li>
 *   <li>request bodies are capped at {@link #MAX_BODY_BYTES} — an unbounded
 *       {@code readAllBytes} on a socket is a trivial memory exhaustion;</li>
 *   <li>the threshold is validated as finite and in range before it reaches the
 *       predicate;</li>
 *   <li>failures return a fixed message; parse details stay in the log, not the
 *       response.</li>
 * </ul>
 *
 * <p>Handlers run on the server's own executor, never on a pipeline thread, so a slow
 * client cannot stall a stage. Everything it reads is either a {@code volatile} field or
 * an immutable snapshot published through an atomic reference, so no locking is needed.
 */
public final class PipelineControlHandler implements HttpHandler {

    /** Header carrying the shared secret. */
    public static final String TOKEN_HEADER = "X-Pipeline-Token";

    /** Largest accepted request body. Generous for {@code {"threshold":60}}, small enough to be safe. */
    public static final int MAX_BODY_BYTES = 8 * 1024;

    /** Field name accepted by {@code POST /threshold}. */
    public static final String FIELD_THRESHOLD = "threshold";

    private static final Logger LOG = Logger.getLogger(PipelineControlHandler.class.getName());

    private static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

    /** Sanity bound on a tunable threshold; wider than any sensor range in {@code SensorType}. */
    private static final double MAX_ABS_THRESHOLD = 1_000_000.0d;

    private final byte[] expectedToken;
    private final MetricsRecorder metrics;
    private final Supplier<AggregateSnapshot> snapshotSupplier;
    private final ThresholdPredicate predicate;
    private final List<MessageChannel> channels;

    /**
     * @param token            shared secret; must match {@code X-Pipeline-Token}
     * @param metrics          counter source for {@code /metrics}
     * @param snapshotSupplier live aggregates for {@code /aggregates}
     * @param predicate        the filter this endpoint may retune
     * @param channels         queues reported by {@code /health}
     */
    public PipelineControlHandler(String token, MetricsRecorder metrics,
                                  Supplier<AggregateSnapshot> snapshotSupplier,
                                  ThresholdPredicate predicate, List<MessageChannel> channels) {
        Objects.requireNonNull(token, "token");
        if (token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.channels = List.copyOf(Objects.requireNonNull(channels, "channels"));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!isAuthorised(exchange)) {
                LOG.log(Level.WARNING, "rejected unauthenticated request to {0}",
                        exchange.getRequestURI().getPath());
                respond(exchange, 401, Json.object(Map.of("error", "unauthorised")));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("GET".equals(method)) {
                handleGet(exchange, path);
            } else if ("POST".equals(method) && "/threshold".equals(path)) {
                handleThreshold(exchange);
            } else {
                respond(exchange, 405, Json.object(Map.of("error", "method not allowed")));
            }
        } catch (RuntimeException e) {
            // A handler exception must never take the server down or leak a stack trace to
            // the client; the detail belongs in the log.
            LOG.log(Level.SEVERE, "control plane handler failed", e);
            respond(exchange, 500, Json.object(Map.of("error", "internal error")));
        } finally {
            exchange.close();
        }
    }

    private void handleGet(HttpExchange exchange, String path) throws IOException {
        switch (path) {
            case "/health" -> respond(exchange, 200, healthJson());
            case "/metrics" -> respond(exchange, 200, metricsJson(metrics.snapshot()));
            case "/aggregates" -> respond(exchange, 200, aggregatesJson(snapshotSupplier.get()));
            // /threshold exists, it just does not answer GET. Falling through to the 404 below
            // would tell an authenticated operator the route is absent and send them looking
            // for a typo, which is the opposite of what a diagnostic response is for.
            case "/threshold" -> respond(exchange, 405, Json.object(Map.of("error", "method not allowed")));
            default -> respond(exchange, 404, Json.object(Map.of("error", "not found")));
        }
    }

    private void handleThreshold(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        if (body == null) {
            respond(exchange, 413, Json.object(Map.of("error", "request body too large")));
            return;
        }
        double requested;
        try {
            Map<String, String> fields = Json.parseFlatObject(body);
            String raw = fields.get(FIELD_THRESHOLD);
            if (raw == null) {
                respond(exchange, 400, Json.object(Map.of("error", "missing field: " + FIELD_THRESHOLD)));
                return;
            }
            requested = Double.parseDouble(raw);
        } catch (IllegalArgumentException e) {
            LOG.log(Level.FINE, "rejected malformed threshold request", e);
            respond(exchange, 400, Json.object(Map.of("error", "body must be {\"threshold\": <number>}")));
            return;
        }
        if (!Double.isFinite(requested) || Math.abs(requested) > MAX_ABS_THRESHOLD) {
            respond(exchange, 400, Json.object(Map.of(
                    "error", "threshold must be finite and within +/-" + (long) MAX_ABS_THRESHOLD)));
            return;
        }
        double previous = predicate.threshold();
        predicate.setThreshold(requested);
        LOG.info(String.format(Locale.ROOT, "threshold changed %.3f -> %.3f via control plane",
                previous, requested));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("previous", previous);
        response.put("current", requested);
        response.put("description", predicate.description());
        respond(exchange, 200, Json.object(response));
    }

    /**
     * Constant-time-ish token check.
     *
     * <p>{@code MessageDigest.isEqual} is length-safe and does not return early on the
     * first mismatching byte. On a loopback interface the timing channel is largely
     * theoretical, but the correct comparison costs nothing.
     */
    private boolean isAuthorised(HttpExchange exchange) {
        String presented = exchange.getRequestHeaders().getFirst(TOKEN_HEADER);
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedToken);
    }

    /** Reads the body, or returns {@code null} if it exceeds {@link #MAX_BODY_BYTES}. */
    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                return null;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private String healthJson() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("status", "up");
        fields.put("filter", predicate.description());
        for (MessageChannel channel : channels) {
            fields.put("queue." + channel.name() + ".depth", channel.depth());
            fields.put("queue." + channel.name() + ".capacity", channel.capacity());
        }
        return Json.object(fields);
    }

    private static String metricsJson(MetricsSnapshot snapshot) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("eventsProduced", snapshot.eventsProduced());
        fields.put("batchesProduced", snapshot.batchesProduced());
        fields.put("eventsPassed", snapshot.eventsPassed());
        fields.put("eventsRejected", snapshot.eventsRejected());
        fields.put("eventsAggregated", snapshot.eventsAggregated());
        fields.put("batchesAggregated", snapshot.batchesAggregated());
        fields.put("errors", snapshot.errors());
        fields.put("blockedMillis", snapshot.totalBlockedNanos() / 1_000_000L);
        fields.put("mostBlockedQueue", snapshot.mostBlockedQueue());
        // Documented as unreliable mid-run: the counters are independent adders.
        fields.put("reconciles", snapshot.reconciles());
        return Json.object(fields);
    }

    private static String aggregatesJson(AggregateSnapshot snapshot) {
        List<String> rows = new ArrayList<>(snapshot.sensorCount());
        for (AggregateResult result : snapshot.sortedBySensorId()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sensorId", result.sensorId());
            row.put("count", result.count());
            row.put("min", result.min());
            row.put("max", result.max());
            row.put("avg", result.average());
            rows.add(Json.object(row));
        }
        return "{\"sensors\":" + rows.size() + ",\"totalCount\":" + snapshot.totalCount()
                + ",\"results\":[" + String.join(",", rows) + "]}";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
        // No caching: every response is a live reading of a moving pipeline.
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
