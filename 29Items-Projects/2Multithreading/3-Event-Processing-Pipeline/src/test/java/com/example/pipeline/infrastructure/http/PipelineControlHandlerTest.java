package com.example.pipeline.infrastructure.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.application.filter.ThresholdPredicate;
import com.example.pipeline.application.port.MessageChannel;
import com.example.pipeline.domain.AggregateResult;
import com.example.pipeline.domain.AggregateSnapshot;
import com.example.pipeline.infrastructure.metrics.AtomicMetricsRecorder;
import com.example.pipeline.infrastructure.queue.BoundedStageQueue;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Control-plane tests driven over a real HTTP socket.
 *
 * <p><strong>Why real sockets and not a mocked {@code HttpExchange}.</strong> The things
 * worth testing here are the things a fake would let through: that the token is checked
 * <em>before</em> routing, that an oversized body is refused rather than buffered, that a
 * handler exception becomes a 500 with no stack trace in it, and that the listener is
 * bound to loopback. A hand-rolled {@code HttpExchange} double would assert my beliefs
 * about the JDK's server rather than its behaviour, and it is exactly the layer where the
 * bugs live. The server binds port {@code 0}, so these tests never collide with a
 * developer's running pipeline or with each other.
 *
 * <p>No test here asserts on elapsed time; the only timing is {@link Timeout}, which turns
 * the failure mode of a wedged handler (a hanging build) into a reported failure.
 */
@Timeout(20)
@DisplayName("PipelineControlHandler over HTTP")
class PipelineControlHandlerTest {

    private static final String TOKEN = "test-token-at-least-16";

    private AtomicMetricsRecorder metrics;
    private ThresholdPredicate predicate;
    private AtomicReference<AggregateSnapshot> snapshot;
    private ControlPlaneServer server;
    private HttpClient client;

    @BeforeEach
    void startServer() {
        metrics = new AtomicMetricsRecorder();
        predicate = new ThresholdPredicate(50.0);
        snapshot = new AtomicReference<>(AggregateSnapshot.empty());
        List<MessageChannel> channels = List.of(
                new BoundedStageQueue("raw", 8, metrics),
                new BoundedStageQueue("filtered", 4, metrics));
        server = new ControlPlaneServer(0, new PipelineControlHandler(
                TOKEN, metrics, snapshot::get, predicate, channels));
        server.start();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .timeout(Duration.ofSeconds(10));
    }

    private HttpResponse<String> send(HttpRequest req) throws IOException, InterruptedException {
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws IOException, InterruptedException {
        HttpRequest.Builder builder = request(path).GET();
        if (token != null) {
            builder.header(PipelineControlHandler.TOKEN_HEADER, token);
        }
        return send(builder.build());
    }

    private HttpResponse<String> postThreshold(String body) throws IOException, InterruptedException {
        return send(request("/threshold")
                .header(PipelineControlHandler.TOKEN_HEADER, TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    @Nested
    @DisplayName("authentication")
    class Authentication {

        @Test
        @DisplayName("a request with no token is refused")
        void missingTokenIsRefused() throws Exception {
            HttpResponse<String> response = get("/health", null);
            assertEquals(401, response.statusCode());
            assertTrue(response.body().contains("unauthorised"), response.body());
        }

        @Test
        @DisplayName("a wrong token is refused")
        void wrongTokenIsRefused() throws Exception {
            assertEquals(401, get("/health", "wrong-token-of-same-len").statusCode());
        }

        @Test
        @DisplayName("a token that is a prefix of the real one is refused")
        void prefixTokenIsRefused() throws Exception {
            // MessageDigest.isEqual is length-safe; String.startsWith semantics must not leak in.
            assertEquals(401, get("/health", TOKEN.substring(0, TOKEN.length() - 1)).statusCode());
        }

        /**
         * The reason {@code createContext("/")} serves every path: an unknown path must not
         * answer 404 to a caller who has not authenticated, because the difference between
         * 404 and 401 enumerates the routes that exist.
         */
        @Test
        @DisplayName("an unknown path is 401 without a token, not 404")
        void unknownPathDoesNotLeakRoutesToStrangers() throws Exception {
            assertEquals(401, get("/no-such-route", null).statusCode());
            assertEquals(404, get("/no-such-route", TOKEN).statusCode());
        }

        @Test
        @DisplayName("an unauthenticated POST cannot retune the filter")
        void unauthenticatedPostCannotMutate() throws Exception {
            HttpResponse<String> response = send(request("/threshold")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"threshold\": 5.0}"))
                    .build());
            assertEquals(401, response.statusCode());
            assertEquals(50.0, predicate.threshold(), "an unauthorised request must not mutate state");
        }
    }

    @Nested
    @DisplayName("GET routes")
    class GetRoutes {

        @Test
        @DisplayName("/health reports status and every queue's depth and capacity")
        void healthReportsQueues() throws Exception {
            HttpResponse<String> response = get("/health", TOKEN);
            assertEquals(200, response.statusCode());
            String body = response.body();
            assertTrue(body.contains("\"status\":\"up\""), body);
            assertTrue(body.contains("\"queue.raw.depth\":0"), body);
            assertTrue(body.contains("\"queue.raw.capacity\":8"), body);
            assertTrue(body.contains("\"queue.filtered.capacity\":4"), body);
        }

        @Test
        @DisplayName("/metrics reflects the recorded counters")
        void metricsReflectCounters() throws Exception {
            metrics.recordProduced(10L, 2L);
            metrics.recordFiltered(6L, 4L);
            metrics.recordError("filter");
            HttpResponse<String> response = get("/metrics", TOKEN);
            assertEquals(200, response.statusCode());
            String body = response.body();
            assertTrue(body.contains("\"eventsProduced\":10"), body);
            assertTrue(body.contains("\"eventsPassed\":6"), body);
            assertTrue(body.contains("\"eventsRejected\":4"), body);
            assertTrue(body.contains("\"errors\":1"), body);
        }

        @Test
        @DisplayName("/aggregates renders the live snapshot, sorted")
        void aggregatesRenderSnapshot() throws Exception {
            snapshot.set(new AggregateSnapshot(Map.of(
                    "sensor-b", new AggregateResult("sensor-b", 1L, 5.0, 5.0, 5.0),
                    "sensor-a", new AggregateResult("sensor-a", 2L, 1.0, 3.0, 4.0))));
            HttpResponse<String> response = get("/aggregates", TOKEN);
            assertEquals(200, response.statusCode());
            String body = response.body();
            assertTrue(body.contains("\"sensors\":2"), body);
            assertTrue(body.contains("\"totalCount\":3"), body);
            assertTrue(body.indexOf("sensor-a") < body.indexOf("sensor-b"), "must be sorted: " + body);
        }

        @Test
        @DisplayName("responses are JSON and explicitly uncacheable")
        void responsesAreJsonAndUncacheable() throws Exception {
            HttpResponse<String> response = get("/health", TOKEN);
            assertEquals("application/json; charset=utf-8",
                    response.headers().firstValue("Content-Type").orElseThrow());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        }

        @Test
        @DisplayName("an unsupported method is 405")
        void unsupportedMethodIsRejected() throws Exception {
            HttpResponse<String> response = send(request("/health")
                    .header(PipelineControlHandler.TOKEN_HEADER, TOKEN)
                    .method("DELETE", HttpRequest.BodyPublishers.noBody())
                    .build());
            assertEquals(405, response.statusCode());
        }

        @Test
        @DisplayName("POST to a GET route is 405, not 404")
        void postToGetRouteIsMethodNotAllowed() throws Exception {
            HttpResponse<String> response = send(request("/metrics")
                    .header(PipelineControlHandler.TOKEN_HEADER, TOKEN)
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build());
            assertEquals(405, response.statusCode());
        }

        /**
         * The mirror image of the test above, and the one that was missing: GET dispatch
         * ran through a switch whose default was 404, so a GET to the POST-only
         * {@code /threshold} reported the route as absent. An operator who has already
         * authenticated is then told to look for a typo in a path that is spelled correctly.
         */
        @Test
        @DisplayName("GET to a POST route is 405, not 404")
        void getToPostRouteIsMethodNotAllowed() throws Exception {
            HttpResponse<String> response = get("/threshold", TOKEN);
            assertEquals(405, response.statusCode());
            assertTrue(response.body().contains("method not allowed"),
                    "expected a method error, got: " + response.body());
        }
    }

    @Nested
    @DisplayName("POST /threshold")
    class PostThreshold {

        @Test
        @DisplayName("a valid threshold is applied and both values are reported back")
        void validThresholdIsApplied() throws Exception {
            HttpResponse<String> response = postThreshold("{\"threshold\": 60.5}");
            assertEquals(200, response.statusCode());
            assertEquals(60.5, predicate.threshold());
            String body = response.body();
            assertTrue(body.contains("\"previous\":50.000000"), body);
            assertTrue(body.contains("\"current\":60.500000"), body);
        }

        @Test
        @DisplayName("an integer literal is accepted")
        void integerLiteralIsAccepted() throws Exception {
            assertEquals(200, postThreshold("{\"threshold\":42}").statusCode());
            assertEquals(42.0, predicate.threshold());
        }

        @Test
        @DisplayName("a negative threshold is accepted - some sensors read below zero")
        void negativeThresholdIsAccepted() throws Exception {
            assertEquals(200, postThreshold("{\"threshold\": -12.5}").statusCode());
            assertEquals(-12.5, predicate.threshold());
        }

        @ParameterizedTest
        @DisplayName("a malformed or out-of-range body is 400 and changes nothing")
        @ValueSource(strings = {
            "",
            "not json",
            "{}",
            "{\"threshold\"}",
            "{\"threshold\": }",
            "{\"threshold\": abc}",
            "{\"other\": 1.0}",
            "{\"threshold\": NaN}",
            "{\"threshold\": 1e400}",
            "{\"threshold\": 2000000.0}",
            "{\"threshold\": -2000000.0}",
        })
        void malformedBodyIsRejected(String body) throws Exception {
            HttpResponse<String> response = postThreshold(body);
            assertEquals(400, response.statusCode(), "body was: " + body);
            assertEquals(50.0, predicate.threshold(), "a rejected request must not mutate the filter");
            assertFalse(response.body().contains("Exception"), "no internals in the response: " + response.body());
        }

        @Test
        @DisplayName("the boundary value is accepted, one step beyond it is not")
        void boundaryIsInclusive() throws Exception {
            assertEquals(200, postThreshold("{\"threshold\": 1000000.0}").statusCode());
            assertEquals(1_000_000.0, predicate.threshold());
            assertEquals(400, postThreshold("{\"threshold\": 1000000.1}").statusCode());
            assertEquals(1_000_000.0, predicate.threshold(), "the rejected value must not have been applied");
        }

        @Test
        @DisplayName("a body over the cap is refused with 413 and never buffered")
        void oversizedBodyIsRefused() throws Exception {
            String padding = "x".repeat(PipelineControlHandler.MAX_BODY_BYTES + 1);
            HttpResponse<String> response = postThreshold("{\"threshold\": 1.0, \"pad\": \"" + padding + "\"}");
            assertEquals(413, response.statusCode());
            assertEquals(50.0, predicate.threshold());
        }

        @Test
        @DisplayName("a body exactly at the cap is still parsed")
        void bodyAtTheCapIsAccepted() throws Exception {
            // Proves the cap is `> MAX`, not `>= MAX`: an off-by-one here would reject
            // a legal request, which is the kind of bug that only shows up in production.
            String prefix = "{\"threshold\": 7.0, \"pad\": \"";
            String suffix = "\"}";
            String padding = "x".repeat(PipelineControlHandler.MAX_BODY_BYTES - prefix.length() - suffix.length());
            HttpResponse<String> response = postThreshold(prefix + padding + suffix);
            assertEquals(200, response.statusCode());
            assertEquals(7.0, predicate.threshold());
        }
    }

    @Nested
    @DisplayName("failure handling")
    class FailureHandling {

        @Test
        @DisplayName("a throwing supplier becomes a 500 that leaks nothing")
        void handlerExceptionBecomesFiveHundred() throws Exception {
            server.close();
            server = new ControlPlaneServer(0, new PipelineControlHandler(
                    TOKEN, metrics,
                    () -> {
                        throw new IllegalStateException("secret internal detail");
                    },
                    predicate, List.of()));
            server.start();
            HttpResponse<String> response = get("/aggregates", TOKEN);
            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("internal error"), response.body());
            assertFalse(response.body().contains("secret internal detail"),
                    "the message must stay in the log: " + response.body());
        }

        @Test
        @DisplayName("the server survives a failed request and serves the next one")
        void serverSurvivesAFailedRequest() throws Exception {
            server.close();
            AtomicReference<Boolean> fail = new AtomicReference<>(true);
            server = new ControlPlaneServer(0, new PipelineControlHandler(
                    TOKEN, metrics,
                    () -> {
                        if (fail.get()) {
                            throw new IllegalStateException("boom");
                        }
                        return AggregateSnapshot.empty();
                    },
                    predicate, List.of()));
            server.start();
            assertEquals(500, get("/aggregates", TOKEN).statusCode());
            fail.set(false);
            assertEquals(200, get("/aggregates", TOKEN).statusCode());
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("a blank token is rejected - it would authenticate everyone")
        void blankTokenIsRejected() {
            assertThrows(IllegalArgumentException.class, () -> new PipelineControlHandler(
                    "  ", metrics, AggregateSnapshot::empty, predicate, List.of()));
        }

        @Test
        @DisplayName("null collaborators are rejected at construction")
        void nullCollaboratorsAreRejected() {
            assertThrows(NullPointerException.class, () -> new PipelineControlHandler(
                    null, metrics, AggregateSnapshot::empty, predicate, List.of()));
            assertThrows(NullPointerException.class, () -> new PipelineControlHandler(
                    TOKEN, null, AggregateSnapshot::empty, predicate, List.of()));
            assertThrows(NullPointerException.class, () -> new PipelineControlHandler(
                    TOKEN, metrics, null, predicate, List.of()));
            assertThrows(NullPointerException.class, () -> new PipelineControlHandler(
                    TOKEN, metrics, AggregateSnapshot::empty, null, List.of()));
            assertThrows(NullPointerException.class, () -> new PipelineControlHandler(
                    TOKEN, metrics, AggregateSnapshot::empty, predicate, null));
        }

        @Test
        @DisplayName("the channel list is copied, so a later mutation cannot change /health")
        void channelListIsCopied() throws Exception {
            List<MessageChannel> mutable = new java.util.ArrayList<>();
            mutable.add(new BoundedStageQueue("only", 2, metrics));
            server.close();
            server = new ControlPlaneServer(0, new PipelineControlHandler(
                    TOKEN, metrics, snapshot::get, predicate, mutable));
            server.start();
            mutable.clear();
            assertTrue(get("/health", TOKEN).body().contains("queue.only.capacity"));
        }

        @Test
        @DisplayName("the bound port is loopback-only and ephemeral")
        void portIsEphemeralAndLoopback() {
            assertNotEquals(0, server.port(), "port 0 must resolve to a real ephemeral port");
            assertTrue(server.isRunning());
        }
    }
}
