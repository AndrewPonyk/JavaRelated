package com.parallelimage.app.api;

import com.parallelimage.app.wiring.ServiceRegistry;
import com.parallelimage.core.engine.EngineStats;
import com.parallelimage.core.io.ImageDiscovery;
import com.parallelimage.core.model.ImageJob;
import com.parallelimage.core.port.JobRepository;
import com.parallelimage.core.progress.ProgressEvent;
import com.parallelimage.core.progress.ProgressListener;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A local control API over {@code com.sun.net.httpserver}. Off by default.
 *
 * <h2>What it is for</h2>
 * Driving a batch from something that is not this process: a scheduled task, a watch-folder script, a
 * Node build step. The alternative — shelling out to {@code java -jar} per batch — pays JVM startup and,
 * more importantly, gets a cold {@code ForkJoinPool} and a cold JIT for every run. A long-lived process
 * with a socket keeps both warm.
 *
 * <h2>Endpoints</h2>
 * <pre>
 * POST /batches             submit a batch; returns 202 + batch id immediately, runs in the background
 * GET  /batches             recent batches from history
 * GET  /batches/{id}        one batch summary
 * GET  /batches/{id}/events SSE stream of that batch's progress, while it is the one running
 * GET  /stats               live pool counters
 * GET  /health              liveness; the only endpoint that needs no token
 * POST /cancel              cancel the running batch
 * </pre>
 *
 * <h2>Security, stated plainly</h2>
 * This is <b>not</b> a hardened server and must not be exposed beyond the loopback interface. Three
 * measures, in decreasing order of what they actually buy:
 * <ol>
 *   <li><b>Bound to {@link InetAddress#getLoopbackAddress()}</b>, not to {@code 0.0.0.0}. This is the real
 *       control: the socket is unreachable from the network regardless of firewall configuration. A
 *       one-word change here would turn an unauthenticated batch runner into a remote one, which is why
 *       the address is a constant in this file rather than a setting.</li>
 *   <li><b>Optional bearer token</b> ({@code api.token} / {@code PIP_API_TOKEN}), compared with a
 *       length-independent constant-time comparison. Its purpose is to stop <em>other local processes</em>
 *       — a browser running untrusted JavaScript can reach a loopback port, and a token it does not know
 *       makes that useless. Absent by default because the loopback bind already excludes the network and
 *       requiring a secret for a local tool nobody enabled is friction with no gain.</li>
 *   <li><b>No CORS headers, ever.</b> Not an omission. Adding {@code Access-Control-Allow-Origin} is
 *       precisely what would let a web page drive this API, and there is no legitimate browser client.</li>
 * </ol>
 * Path arguments are <em>not</em> confined to a sandbox — see {@link JobRequestValidator}'s class javadoc
 * for why that would be theatre rather than security, and what would have to change first.
 *
 * <h2>One batch at a time</h2>
 * {@code POST /batches} is serialised by {@link #running}: a second submission while one is in flight gets
 * {@code 409 Conflict}. Two concurrent batches would share one pool, so they would neither run faster than
 * sequentially nor report meaningful per-batch timings, and their cancellation would be indistinguishable
 * — {@code ImageProcessingEngine#cancel} cancels <em>the</em> batch, not a named one.
 */
public final class BatchJobController implements AutoCloseable {

    private static final Logger LOG = System.getLogger(BatchJobController.class.getName());

    /** Not configurable. See the class javadoc. */
    private static final InetAddress BIND_ADDRESS = InetAddress.getLoopbackAddress();

    /** Rows returned by the history endpoints unless {@code ?limit=} says otherwise. */
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    /** A submission body is a handful of paths and options; anything past this is not a real request. */
    private static final int MAX_BODY_BYTES = 1024 * 1024;

    /** Grace period for in-flight requests at shutdown. */
    private static final int STOP_DELAY_SECONDS = 2;

    private final ServiceRegistry.Registry registry;
    private final Optional<String> token;
    private final HttpServer server;
    private final ExecutorService requestExecutor;
    private final ExecutorService batchExecutor;

    /**
     * The batch currently in flight, or {@code null}.
     *
     * <p>An {@link AtomicReference} rather than a boolean plus a field: claiming the slot and recording
     * what claimed it has to be one atomic step, or two simultaneous submissions can both see "free".
     */
    private final AtomicReference<String> running = new AtomicReference<>();

    /** The SSE fan-out for {@link #running}'s batch, or {@code null} between batches. */
    private final AtomicReference<ProgressHub> activeHub = new AtomicReference<>();

    private BatchJobController(ServiceRegistry.Registry registry, HttpServer server,
            ExecutorService requestExecutor, ExecutorService batchExecutor) {
        this.registry = registry;
        this.token = registry.config().apiToken();
        this.server = server;
        this.requestExecutor = requestExecutor;
        this.batchExecutor = batchExecutor;
    }

    /**
     * Binds the port and starts serving.
     *
     * <p>A small fixed thread pool, not a virtual-thread executor and not the engine's pool. Requests are
     * either trivial (stats, health) or spend their entire life blocked in {@code engine.process}, and
     * running them on the fork/join pool would occupy a worker that the batch itself needs. Four threads
     * is enough for a control plane whose write endpoint is serialised anyway.
     *
     * @throws IOException if the port is already in use — normally a second instance of the application
     */
    public static BatchJobController start(ServiceRegistry.Registry registry) throws IOException {
        int port = registry.config().apiPort();
        HttpServer server = HttpServer.create(new InetSocketAddress(BIND_ADDRESS, port), 0);

        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "pip-api");
            thread.setDaemon(true);
            return thread;
        };
        ExecutorService executor = Executors.newFixedThreadPool(4, factory);
        server.setExecutor(executor);

        // One dedicated thread: `running` already allows only one batch at a time, so a bigger pool would
        // just sit idle. Separate from `executor` so a batch in progress never occupies a request thread
        // that GET /stats or an SSE subscriber needs.
        ThreadFactory batchFactory = runnable -> {
            Thread thread = new Thread(runnable, "pip-api-batch");
            thread.setDaemon(true);
            return thread;
        };
        ExecutorService batchExecutor = Executors.newSingleThreadExecutor(batchFactory);

        BatchJobController controller = new BatchJobController(registry, server, executor, batchExecutor);
        server.createContext("/health", controller::handleHealth);
        server.createContext("/stats", controller::handleStats);
        server.createContext("/batches", controller::handleBatches);
        server.createContext("/cancel", controller::handleCancel);
        server.start();

        LOG.log(Level.INFO, () -> "control API on http://" + BIND_ADDRESS.getHostAddress() + ":"
                + server.getAddress().getPort()
                + (controller.token.isPresent() ? " (token required)" : " (no token; loopback only)"));
        return controller;
    }

    /** The port actually bound. Differs from the configured one when that was 0. */
    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        // stop() waits up to the delay for in-flight exchanges, then kills them. A batch running inside a
        // request is cancelled by the engine's own shutdown, which the registry performs after this.
        server.stop(STOP_DELAY_SECONDS);
        requestExecutor.shutdownNow();
        batchExecutor.shutdownNow();
    }

    // ------------------------------------------------------------------------
    //  Handlers
    // ------------------------------------------------------------------------

    /**
     * Liveness. The only endpoint exempt from the token.
     *
     * <p>A health check that needs a credential is a health check that a supervisor cannot perform, and it
     * reveals nothing: whether this port answers is already observable by connecting to it.
     */
    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "GET")) {
            return;
        }
        respond(exchange, 200, Json.object(new LinkedHashMap<>(Map.of(
                "status", "ok",
                "parallelism", registry.engine().parallelism(),
                "historyEnabled", registry.historyEnabled(),
                "enhancer", registry.enhancerDescription(),
                "batchRunning", running.get() != null))));
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        if (!authorised(exchange) || !requireMethod(exchange, "GET")) {
            return;
        }
        EngineStats stats = registry.engine().stats();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parallelism", stats.parallelism());
        body.put("activeThreads", stats.activeThreads());
        body.put("runningThreads", stats.runningThreads());
        body.put("queuedTasks", stats.queuedTasks());
        body.put("queuedSubmissions", stats.queuedSubmissions());
        body.put("stealCount", stats.stealCount());
        body.put("metadataEntries", stats.metadataEntries());
        body.put("optimisticReadRate", stats.optimisticReadRate());
        body.put("shenandoahActive", stats.shenandoahActive());
        body.put("heapUtilisation", stats.heapUtilisation());
        body.put("gcTimeMillis", stats.gcTimeMillis());
        body.put("utilisation", stats.utilisation());
        body.put("runningBatch", running.get());
        respond(exchange, 200, Json.object(body));
    }

    /** {@code POST} submits, {@code GET} reads history; {@code /batches/{id}} reads one. */
    private void handleBatches(HttpExchange exchange) throws IOException {
        if (!authorised(exchange)) {
            return;
        }
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String tail = path.length() > "/batches".length() ? path.substring("/batches".length() + 1) : "";

        if (method.equals("POST") && tail.isEmpty()) {
            handleSubmit(exchange);
        } else if (method.equals("GET") && tail.isEmpty()) {
            handleListBatches(exchange);
        } else if (method.equals("GET") && tail.endsWith("/events")) {
            handleEvents(exchange, tail.substring(0, tail.length() - "/events".length()));
        } else if (method.equals("GET")) {
            handleFindBatch(exchange, tail);
        } else {
            respondError(exchange, 405, "use POST /batches or GET /batches[/{id}[/events]]");
        }
    }

    /**
     * Submits a batch. Returns {@code 202} with the batch id as soon as it is accepted; the batch itself
     * runs on a background thread.
     *
     * <h2>Why asynchronous</h2>
     * A blocking call with a real exit status is simpler for a script that only wants to know whether the
     * batch succeeded — but it gives that script no way to observe progress on anything longer than an
     * HTTP client's own timeout, and it is what stands between this endpoint and {@code GET
     * /batches/{id}/events} having anything to stream. The batch id is in the response either way; a
     * client that wants the final outcome polls {@code GET /batches/{id}} once history is enabled, or
     * watches the event stream while the batch is the one running.
     */
    private void handleSubmit(HttpExchange exchange) throws IOException {
        Map<String, String> fields;
        try {
            fields = Json.parseFlatObject(readBody(exchange));
        } catch (Json.JsonException e) {
            respondError(exchange, 400, e.getMessage());
            return;
        }

        JobRequestValidator.Result validation =
                JobRequestValidator.validate(fields, registry.defaultOptions());
        if (!validation.valid()) {
            respond(exchange, 400, Json.object(Map.of(
                    "error", "validation failed",
                    "problems", validation.problems())));
            return;
        }

        JobRequestValidator.BatchRequest request = validation.request().orElseThrow();
        List<ImageJob> jobs = ImageDiscovery.plan(
                request.input(), request.output(), request.options(), request.recursive());

        if (jobs.isEmpty()) {
            // 200, not 404. "Nothing matched" is a valid answer about a valid directory, and a watch-folder
            // script polling an empty directory should not be reading error responses as its normal case.
            respond(exchange, 200, Json.object(Map.of(
                    "batchId", "",
                    "total", 0,
                    "message", "no images matched")));
            return;
        }

        String batchId = jobs.get(0).batchId();
        if (!running.compareAndSet(null, batchId)) {
            respondError(exchange, 409, "a batch is already running (" + running.get() + ")");
            return;
        }

        try {
            Files.createDirectories(request.output());
        } catch (IOException e) {
            running.set(null);
            respondError(exchange, 500, "cannot create output directory: " + e.getMessage());
            return;
        }

        ProgressHub hub = new ProgressHub();
        activeHub.set(hub);
        batchExecutor.execute(() -> runInBackground(batchId, jobs, hub));

        respond(exchange, 202, Json.object(Map.of(
                "batchId", batchId,
                "total", jobs.size())));
    }

    /**
     * Runs one batch to completion off the request thread that accepted it.
     *
     * <p>The engine records the outcome via the repository exactly as a blocking call would; there is no
     * response left to write here, only history and any attached SSE subscribers to tell.
     */
    private void runInBackground(String batchId, List<ImageJob> jobs, ProgressHub hub) {
        try {
            registry.engine().process(jobs, hub);
        } catch (RuntimeException e) {
            // The engine converts per-image failures into outcomes, so reaching here means something
            // structural went wrong -- a pool that will not accept work, an unreadable input root. Nothing
            // to respond to any more, so this is a log, not a 500.
            LOG.log(Level.WARNING, () -> "batch " + batchId + " failed structurally: " + e);
        } finally {
            running.set(null);
            activeHub.compareAndSet(hub, null);
        }
    }

    /**
     * Streams the running batch's progress as {@code text/event-stream}, one {@code data:} line per
     * {@link ProgressEvent}, flushed immediately. {@code 404} if {@code batchId} is not the batch currently
     * in {@link #running} — including "already finished", since this API keeps only one batch's live
     * stream at a time.
     */
    private void handleEvents(HttpExchange exchange, String batchId) throws IOException {
        if (!requireMethod(exchange, "GET")) {
            return;
        }
        ProgressHub hub = activeHub.get();
        if (batchId.isEmpty() || hub == null || !batchId.equals(running.get())) {
            respondError(exchange, 404, "no running batch with id " + batchId);
            return;
        }

        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);

        Subscriber subscriber = hub.subscribe();
        try (OutputStream out = exchange.getResponseBody()) {
            streamEvents(out, subscriber, batchId);
        } finally {
            hub.unsubscribe(subscriber);
        }
    }

    /**
     * Writes drained events until {@code BATCH_FINISHED} is seen, or the batch stops being the one running
     * (it finished and rotated off before this client ever saw the finishing event). A write failure — the
     * client disconnected — propagates as an {@link IOException}, which {@link #handleEvents} lets close
     * the exchange rather than trying to answer it.
     */
    private void streamEvents(OutputStream out, Subscriber subscriber, String batchId) throws IOException {
        while (true) {
            List<ProgressEvent> events;
            try {
                events = subscriber.awaitAndDrain(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            for (ProgressEvent event : events) {
                writeEvent(out, event);
                if (event.phase() == ProgressEvent.Phase.BATCH_FINISHED) {
                    return;
                }
            }
            if (events.isEmpty() && !batchId.equals(running.get())) {
                return;
            }
        }
    }

    private static void writeEvent(OutputStream out, ProgressEvent event) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("batchId", event.batchId());
        body.put("jobId", event.jobId());
        body.put("phase", event.phase().name());
        body.put("completed", event.completed());
        body.put("total", event.total());
        body.put("detail", event.detail());
        out.write(("data: " + Json.object(body) + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /**
     * Fans out one running batch's {@link ProgressEvent}s to every SSE client currently attached to it.
     *
     * <p>A {@link ProgressListener} itself, so it plugs straight into {@code
     * ImageProcessingEngine.process(List, ProgressListener)}. Per-client coalescing lives on
     * {@link Subscriber}, not here, because two clients attaching at different times must not affect what
     * the other one sees.
     */
    private static final class ProgressHub implements ProgressListener {
        private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

        @Override
        public void onProgress(ProgressEvent event) {
            // Never throw, per ProgressListener's contract: this runs on a fork/join worker, and one
            // misbehaving subscriber must not stall it.
            try {
                for (Subscriber subscriber : subscribers) {
                    subscriber.offer(event);
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, () -> "SSE fan-out failed: " + e);
            }
        }

        Subscriber subscribe() {
            Subscriber subscriber = new Subscriber();
            subscribers.add(subscriber);
            return subscriber;
        }

        void unsubscribe(Subscriber subscriber) {
            subscribers.remove(subscriber);
        }
    }

    /**
     * One SSE client's view of the batch, coalesced the same way {@code pip-ui}'s {@code ProgressBridge}
     * does: rare/notable events queue losslessly, the high-frequency {@code JOB_STARTED}/
     * {@code JOB_COMPLETED} pair collapses into whichever is most recent — safe because
     * {@link ProgressEvent#completed()} and {@link ProgressEvent#total()} are absolute counts, not deltas.
     * The difference from {@code ProgressBridge} is what drains it: an FX {@code AnimationTimer} pulse
     * there, the SSE request thread's own wait/drain loop here.
     */
    private static final class Subscriber {
        private static final int MAX_PENDING = 512;

        private final Queue<ProgressEvent> notable = new ConcurrentLinkedQueue<>();
        private final AtomicInteger notableSize = new AtomicInteger();
        private final AtomicReference<ProgressEvent> latest = new AtomicReference<>();
        private final Semaphore wakeup = new Semaphore(0);

        void offer(ProgressEvent event) {
            if (event.isBatchLevel()
                    || event.phase() == ProgressEvent.Phase.JOB_FAILED
                    || event.phase() == ProgressEvent.Phase.JOB_CANCELLED) {
                if (notableSize.incrementAndGet() > MAX_PENDING) {
                    notableSize.decrementAndGet();
                } else {
                    notable.add(event);
                }
            } else {
                latest.set(event);
            }
            wakeup.release();
        }

        /** Blocks up to {@code timeoutMillis} for something to arrive, then drains whatever is pending. */
        List<ProgressEvent> awaitAndDrain(long timeoutMillis) throws InterruptedException {
            boolean signaled = wakeup.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!signaled) {
                LOG.log(Level.TRACE, () -> "sse subscriber poll timed out after " + timeoutMillis + "ms");
            }
            wakeup.drainPermits();
            List<ProgressEvent> drained = new ArrayList<>();
            ProgressEvent event;
            while ((event = notable.poll()) != null) {
                notableSize.decrementAndGet();
                drained.add(event);
            }
            ProgressEvent frequent = latest.getAndSet(null);
            if (frequent != null) {
                drained.add(frequent);
            }
            return drained;
        }
    }

    /**
     * A pre-rendered JSON fragment.
     *
     * <p>{@link Json} quotes anything it does not recognise, so a nested object built as a string would
     * arrive as an escaped string literal. This wrapper's {@code toString} is the fragment itself, which
     * is exactly what {@code Json.value}'s fallback emits — one small type instead of a nesting-aware
     * writer this API does not need.
     */
    private record RawJson(String rendered) {
        @Override
        public String toString() {
            return rendered;
        }
    }

    private void handleListBatches(HttpExchange exchange) throws IOException {
        int limit = limitParameter(exchange);
        List<String> rendered = registry.repository().recentBatches(limit).stream()
                .map(BatchJobController::describeBatch)
                .toList();
        respond(exchange, 200, Json.object(Map.of(
                "count", rendered.size(),
                "historyEnabled", registry.historyEnabled(),
                "batches", rendered.stream().map(RawJson::new).toList())));
    }

    private void handleFindBatch(HttpExchange exchange, String batchId) throws IOException {
        Optional<JobRepository.BatchSummary> found = registry.repository().findBatch(batchId);
        if (found.isEmpty()) {
            // Explicitly distinguishes "no such batch" from "history is off", because with history off
            // every id is missing and a bare 404 sends the client looking for the wrong problem.
            respondError(exchange, 404, registry.historyEnabled()
                    ? "no such batch: " + batchId
                    : "history is disabled; no batch can be looked up");
            return;
        }
        respond(exchange, 200, describeBatch(found.get()));
    }

    private static String describeBatch(JobRepository.BatchSummary summary) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("batchId", summary.batchId());
        body.put("total", summary.total());
        body.put("succeeded", summary.succeeded());
        body.put("failed", summary.failed());
        body.put("cancelled", summary.cancelled());
        body.put("wallClockMillis", summary.wallClockMillis());
        body.put("successRate", summary.successRate());
        body.put("startedAt", summary.startedAt() == null ? null : summary.startedAt().toString());
        return Json.object(body);
    }

    /**
     * Cancels the running batch.
     *
     * <p>{@code 200} whether or not one was running, with a {@code cancelled} flag saying which. A client
     * cancelling twice, or racing a batch that has just finished, is not making an error — and a 404 would
     * force it to treat a successful outcome as a failure.
     */
    private void handleCancel(HttpExchange exchange) throws IOException {
        if (!authorised(exchange) || !requireMethod(exchange, "POST")) {
            return;
        }
        String batchId = running.get();
        boolean cancelled = registry.engine().cancel();
        respond(exchange, 200, Json.object(new LinkedHashMap<>(Map.of(
                "cancelled", cancelled,
                "batchId", batchId == null ? "" : batchId))));
    }

    // ------------------------------------------------------------------------
    //  Plumbing
    // ------------------------------------------------------------------------

    /**
     * Checks the bearer token when one is configured.
     *
     * <p>Compared with {@link java.security.MessageDigest#isEqual} over UTF-8 bytes: {@link String#equals}
     * returns as soon as it finds a differing character, and that timing difference is the textbook way to
     * recover a secret one byte at a time. The threat is remote in a loopback-only server, but the fix is
     * one method call and the code is read as an example by whoever next writes an authenticated endpoint.
     */
    private boolean authorised(HttpExchange exchange) throws IOException {
        if (token.isEmpty()) {
            return true;
        }
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        String presented = header != null && header.startsWith("Bearer ")
                ? header.substring("Bearer ".length()).trim()
                : "";
        boolean ok = java.security.MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                token.get().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            // No detail about what was wrong with it, and the 401 carries WWW-Authenticate so a client
            // knows the scheme. Never log the presented value: a token in a log file is a leaked token.
            exchange.getResponseHeaders().add("WWW-Authenticate", "Bearer");
            respondError(exchange, 401, "missing or invalid bearer token");
        }
        return ok;
    }

    private boolean requireMethod(HttpExchange exchange, String expected) throws IOException {
        if (exchange.getRequestMethod().equals(expected)) {
            return true;
        }
        exchange.getResponseHeaders().add("Allow", expected);
        respondError(exchange, 405, "use " + expected);
        return false;
    }

    /** Reads {@code ?limit=N}, clamped. An unparseable value falls back rather than 400-ing. */
    private static int limitParameter(HttpExchange exchange) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            return DEFAULT_LIMIT;
        }
        for (String pair : query.split("&")) {
            if (!pair.startsWith("limit=")) {
                continue;
            }
            try {
                int parsed = Integer.parseInt(pair.substring("limit=".length()));
                return Math.clamp(parsed, 1, MAX_LIMIT);
            } catch (NumberFormatException e) {
                return DEFAULT_LIMIT;
            }
        }
        return DEFAULT_LIMIT;
    }

    /**
     * Reads the request body, refusing anything over {@link #MAX_BODY_BYTES}.
     *
     * <p>{@code readAllBytes()} has no such limit — a client (malicious or just wrong) sending a
     * multi-gigabyte body would otherwise be materialised into memory in full before validation ever
     * gets a chance to reject it. Reading one byte past the cap and checking the count avoids that
     * without needing a counting wrapper stream.
     */
    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new Json.JsonException("request body exceeds " + MAX_BODY_BYTES + " bytes");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * Writes a response and closes the exchange.
     *
     * <p>The byte length is computed from the encoded bytes, not from {@code String#length}. A failure
     * message containing a non-ASCII character — a path with an accent, a watermark string — makes the two
     * differ, and an undersized {@code Content-Length} truncates the body mid-character.
     */
    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void respondError(HttpExchange exchange, int status, String message) throws IOException {
        respond(exchange, status, Json.object(Map.of("error", message)));
    }

    /**
     * Blocks until the server stops. Used by {@code --serve}, which has nothing else to do.
     *
     * <p>An interrupt returns rather than propagating: the caller is {@code main}, the shutdown hook has
     * already been registered, and rethrowing would only replace an orderly exit with a stack trace.
     */
    public void awaitShutdown() {
        try {
            // A latch that is never counted down would work equally well; using the executor means the
            // wait ends by itself when close() shuts it down, with no extra field to keep in sync.
            requestExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
