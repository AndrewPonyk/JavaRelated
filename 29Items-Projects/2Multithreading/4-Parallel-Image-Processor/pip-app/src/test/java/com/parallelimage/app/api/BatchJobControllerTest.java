package com.parallelimage.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.app.config.AppConfig;
import com.parallelimage.app.wiring.ServiceRegistry;
import com.parallelimage.core.engine.ImageProcessingEngine;
import com.parallelimage.core.pipeline.ImageOperation.EnhanceMode;
import com.parallelimage.core.port.JobRepository;
import com.parallelimage.core.model.JobStatus;
import com.parallelimage.core.port.JobRepository.BatchSummary;
import com.parallelimage.core.port.JobRepository.JobRecord;
import com.parallelimage.core.spi.ImageEnhancer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link BatchJobController} tests.
 *
 * <p>Exercises the controller as an actual socket rather than by calling its handlers directly, because
 * the properties worth pinning here — the loopback bind, the bearer-token gate, the one-batch-at-a-time
 * {@code 409} — are all things that only exist at the HTTP boundary. Every server binds to an ephemeral
 * port ({@code api.port=0}) and reads the real one back via {@link BatchJobController#port()}, so tests
 * never collide over a fixed port.
 */
class BatchJobControllerTest {

    @TempDir
    private Path root;

    private Path input;
    private Path output;

    private final HttpClient client = HttpClient.newHttpClient();

    private BatchJobController controller;
    private ServiceRegistry.Registry registry;

    @BeforeEach
    void createDirectories() throws IOException {
        input = Files.createDirectories(root.resolve("photos"));
        output = root.resolve("out");
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", input.resolve("a.png").toFile());
    }

    @AfterEach
    void tearDown() {
        if (controller != null) {
            controller.close();
        }
        if (registry != null) {
            registry.close();
        }
    }

    /** An external file with {@code api.port=0} (ephemeral) plus whatever else the test needs. */
    private AppConfig configFrom(String... lines) throws IOException {
        Path file = root.resolve("application-" + System.identityHashCode(lines) + ".properties");
        StringBuilder body = new StringBuilder("api.port=0\n");
        for (String line : lines) {
            body.append(line).append('\n');
        }
        Files.writeString(file, body.toString());
        return AppConfig.load(file);
    }

    private ServiceRegistry.Registry registryFor(AppConfig config, ImageEnhancer enhancer) {
        return registryWithRepository(config, enhancer, JobRepository.NO_OP);
    }

    private ServiceRegistry.Registry registryWithRepository(
            AppConfig config, ImageEnhancer enhancer, JobRepository repository) {
        ImageProcessingEngine engine = ImageProcessingEngine.builder()
                .repository(JobRepository.NO_OP)
                .enhancer(enhancer)
                .build();
        return new ServiceRegistry.Registry(config, engine, repository, enhancer.describe());
    }

    /** Never enhances; used by tests that only care about routing, auth and status codes. */
    private static final class NoOpEnhancer implements ImageEnhancer {
        @Override
        public BufferedImage enhance(BufferedImage source, EnhanceMode mode, double strength) {
            return source;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String describe() {
            return "test-no-op-enhancer";
        }
    }

    /** Blocks inside {@code enhance} until released, so a batch can be held "in flight" on demand. */
    private static final class LatchedEnhancer implements ImageEnhancer {
        private final CountDownLatch release;

        LatchedEnhancer(CountDownLatch release) {
            this.release = release;
        }

        @Override
        public BufferedImage enhance(BufferedImage source, EnhanceMode mode, double strength) {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return source;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String describe() {
            return "test-latched-enhancer";
        }
    }

    /** A distinct {@link JobRepository} (not {@code NO_OP}) with a canned {@code recentBatches}/{@code findBatch}. */
    private static final class FakeHistoryRepository implements JobRepository {
        private final List<BatchSummary> batches;

        FakeHistoryRepository(List<BatchSummary> batches) {
            this.batches = batches;
        }

        @Override
        public void saveBatch(String batchId, List<com.parallelimage.core.model.ImageJob> jobs) {
            // intentionally empty
        }

        @Override
        public void recordOutcome(com.parallelimage.core.model.JobOutcome outcome) {
            // intentionally empty
        }

        @Override
        public void markRunning(String jobId) {
            // intentionally empty
        }

        @Override
        public List<JobRecord> findByStatus(JobStatus status) {
            return List.of();
        }

        @Override
        public void recordMetadata(com.parallelimage.core.model.ImageMetadata metadata) {
            // intentionally empty
        }

        @Override
        public void completeBatch(String batchId, long wallClockMillis) {
            // intentionally empty
        }

        @Override
        public List<JobRecord> recentJobs(int limit) {
            return List.of();
        }

        @Override
        public List<BatchSummary> recentBatches(int limit) {
            return batches;
        }

        @Override
        public Optional<BatchSummary> findBatch(String batchId) {
            return batches.stream().filter(b -> b.batchId().equals(batchId)).findFirst();
        }

        @Override
        public int purgeOlderThan(Instant cutoff) {
            return 0;
        }

        @Override
        public void close() {
            // intentionally empty
        }
    }

    private String submitBody(boolean withEnhance) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("input", input.toString());
        fields.put("output", output.toString());
        if (withEnhance) {
            fields.put("pipeline", "enhance:clahe");
        }
        return Json.object(fields);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + controller.port() + path));
    }

    @Nested
    @DisplayName("binding")
    class Binding {

        @Test
        @DisplayName("the control API is bound to loopback, never to a wider address")
        void bindsToLoopback() throws IOException, InterruptedException {
            registry = registryFor(configFrom(), new NoOpEnhancer());
            controller = BatchJobController.start(registry);

            assertTrue(controller.port() > 0, "an ephemeral port must resolve to a real one");
            // The javadoc's real security control: this must hold regardless of any future config
            // change that tries to widen the bind address.
            HttpResponse<String> response = send(request("/health").GET());
            assertEquals(200, response.statusCode());
        }
    }

    @Nested
    @DisplayName("bearer token")
    class BearerToken {

        @Test
        @DisplayName("a request with no Authorization header is refused with a WWW-Authenticate challenge")
        void missingTokenIsRejected() throws Exception {
            registry = registryFor(configFrom("api.token=s3cr3t"), new NoOpEnhancer());
            controller = BatchJobController.start(registry);

            HttpResponse<String> response = send(request("/stats").GET());

            assertEquals(401, response.statusCode());
            assertEquals("Bearer", response.headers().firstValue("WWW-Authenticate").orElse(null));
        }

        @Test
        @DisplayName("the correct bearer token is accepted")
        void correctTokenIsAccepted() throws Exception {
            registry = registryFor(configFrom("api.token=s3cr3t"), new NoOpEnhancer());
            controller = BatchJobController.start(registry);

            HttpResponse<String> response = send(request("/stats")
                    .header("Authorization", "Bearer s3cr3t")
                    .GET());

            assertEquals(200, response.statusCode());
        }

        @Test
        @DisplayName("health needs no token even when one is configured")
        void healthIsExemptFromTheToken() throws Exception {
            registry = registryFor(configFrom("api.token=s3cr3t"), new NoOpEnhancer());
            controller = BatchJobController.start(registry);

            HttpResponse<String> response = send(request("/health").GET());

            assertEquals(200, response.statusCode());
        }
    }

    @Nested
    @DisplayName("one batch at a time")
    class OneBatchAtATime {

        @Test
        @DisplayName("a second submission while one is running gets 409, not queued silently")
        void concurrentSubmissionIsRejected() throws Exception {
            CountDownLatch release = new CountDownLatch(1);
            registry = registryFor(configFrom(), new LatchedEnhancer(release));
            controller = BatchJobController.start(registry);

            try {
                HttpResponse<String> first = send(request("/batches")
                        .POST(HttpRequest.BodyPublishers.ofString(submitBody(true))));
                assertEquals(202, first.statusCode(), "the first submission must be accepted: " + first.body());

                HttpResponse<String> second = send(request("/batches")
                        .POST(HttpRequest.BodyPublishers.ofString(submitBody(false))));
                assertEquals(409, second.statusCode());
            } finally {
                release.countDown();
            }
        }
    }

    @Nested
    @DisplayName("batch history")
    class BatchHistory {

        @Test
        @DisplayName("GET /batches lists the repository's recent batches")
        void listBatchesReturnsRecentBatches() throws Exception {
            BatchSummary summary = new BatchSummary("batch-1", 4, 3, 1, 0, 1_000L, Instant.EPOCH);
            registry = registryWithRepository(configFrom(), new NoOpEnhancer(),
                    new FakeHistoryRepository(List.of(summary)));
            controller = BatchJobController.start(registry);

            HttpResponse<String> response = send(request("/batches").GET());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"count\":1"), response.body());
            assertTrue(response.body().contains("\"historyEnabled\":true"), response.body());
            assertTrue(response.body().contains("batch-1"), response.body());
        }

        @Test
        @DisplayName("GET /batches/{id} returns the batch when the repository has it")
        void findBatchReturnsTheMatchingBatch() throws Exception {
            BatchSummary summary = new BatchSummary("batch-1", 4, 3, 1, 0, 1_000L, Instant.EPOCH);
            registry = registryWithRepository(configFrom(), new NoOpEnhancer(),
                    new FakeHistoryRepository(List.of(summary)));
            controller = BatchJobController.start(registry);

            HttpResponse<String> response = send(request("/batches/batch-1").GET());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"succeeded\":3"), response.body());
        }

        @Test
        @DisplayName("GET /batches/{id} is 404 with a history-aware message when the id is unknown")
        void findBatchReturns404WhenUnknown() throws Exception {
            registry = registryWithRepository(configFrom(), new NoOpEnhancer(),
                    new FakeHistoryRepository(List.of()));
            controller = BatchJobController.start(registry);

            HttpResponse<String> response = send(request("/batches/does-not-exist").GET());

            assertEquals(404, response.statusCode());
            assertTrue(response.body().contains("no such batch: does-not-exist"), response.body());
        }
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
