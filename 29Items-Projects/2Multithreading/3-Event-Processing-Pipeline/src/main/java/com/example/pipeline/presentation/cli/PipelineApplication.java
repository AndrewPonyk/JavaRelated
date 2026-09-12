package com.example.pipeline.presentation.cli;

import com.example.pipeline.application.PipelineOrchestrator;
import com.example.pipeline.application.PipelineReport;
import com.example.pipeline.application.filter.ThresholdPredicate;
import com.example.pipeline.application.port.AggregateRepository;
import com.example.pipeline.application.port.AggregateSink;
import com.example.pipeline.application.port.MessageChannel;
import com.example.pipeline.application.port.RateLimiter;
import com.example.pipeline.application.stage.AggregationStage;
import com.example.pipeline.application.stage.FilterStage;
import com.example.pipeline.application.stage.ProducerStage;
import com.example.pipeline.infrastructure.concurrent.PipelineExecutors;
import com.example.pipeline.infrastructure.config.ConfigLoader;
import com.example.pipeline.infrastructure.config.ConfigurationException;
import com.example.pipeline.infrastructure.config.PipelineConfig;
import com.example.pipeline.infrastructure.generator.SyntheticSensorEventGenerator;
import com.example.pipeline.infrastructure.generator.TokenBucketRateLimiter;
import com.example.pipeline.infrastructure.http.ControlPlaneServer;
import com.example.pipeline.infrastructure.http.PipelineControlHandler;
import com.example.pipeline.infrastructure.metrics.AtomicMetricsRecorder;
import com.example.pipeline.infrastructure.persistence.InMemoryAggregateRepository;
import com.example.pipeline.infrastructure.queue.BoundedStageQueue;
import com.example.pipeline.infrastructure.sink.CompositeAggregateSink;
import com.example.pipeline.infrastructure.sink.ConsoleAggregateSink;
import com.example.pipeline.infrastructure.sink.CsvAggregateSink;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point: reads configuration, builds the object graph, runs one pipeline, exits.
 *
 * <p><strong>This is the only class that knows every concrete type.</strong> Composition
 * lives at the outermost layer so nothing inside depends on a choice made here: the stages
 * see {@code MessageChannel}, {@code RateLimiter}, {@code AggregateSink} and
 * {@code AggregateRepository}, never {@code ArrayBlockingQueue}, {@code CsvAggregateSink}
 * or a JDBC URL. Manual wiring rather than a DI container — the graph is a dozen objects
 * constructed once, and reflective injection would add a dependency, a startup cost and a
 * class of runtime-only failure in exchange for nothing.
 *
 * <p><strong>Exit codes are the contract</strong> (see {@code docs/ARCHITECTURE.md} §2.6),
 * because a CI job reads the exit status, not the log:
 * <ul>
 *   <li>{@code 0} — every stage drained and the counters reconcile;</li>
 *   <li>{@code 1} — the run failed, or events were lost;</li>
 *   <li>{@code 2} — configuration was invalid; <em>nothing was started</em>;</li>
 *   <li>{@code 130} — interrupted ({@code Ctrl+C}) after a clean drain, the conventional
 *       {@code 128 + SIGINT}.</li>
 * </ul>
 *
 * <p><strong>{@code Ctrl+C} is a drain, not a kill.</strong> The shutdown hook calls
 * {@link PipelineOrchestrator#requestStopAll()} and then <em>waits</em> for the run to
 * finish, because the JVM terminates as soon as every hook returns. Without that wait the
 * in-flight batches would be discarded and the report never printed; with it, an
 * interrupted run still aggregates what it has, prints the table and exits {@code 130}.
 * The wait is bounded by the shutdown timeout plus a margin, so a genuinely stuck pipeline
 * cannot make {@code Ctrl+C} hang the terminal for ever.
 *
 * <p>Ordering matters in two places, both load-bearing: the queues are created before the
 * stages (the stages hold them), and the control plane is started <em>after</em> the stages
 * exist but stopped <em>before</em> the pools shut down, so a handler can never touch a
 * half-torn-down pipeline.
 */
public final class PipelineApplication {

    /** CSV report file name inside {@code pipeline.output.dir}. */
    public static final String CSV_FILE_NAME = "aggregates.csv";

    /** Name of the producer-to-filter queue, used in metrics and the dashboard. */
    public static final String QUEUE_RAW = "raw";

    /** Name of the filter-to-aggregation queue. */
    public static final String QUEUE_FILTERED = "filtered";

    private static final Logger LOG = Logger.getLogger(PipelineApplication.class.getName());

    /** Extra time the shutdown hook allows beyond the configured drain timeout. */
    private static final long HOOK_MARGIN_MILLIS = 1_000L;

    private PipelineApplication() {
    }

    /**
     * Runs one pipeline and terminates the JVM with the report's exit code.
     *
     * @param args {@code --key=value} overrides; {@code --help} prints usage
     */
    public static void main(String[] args) {
        PipelineConfig config;
        try {
            CliArguments arguments = CliArguments.parse(args);
            if (arguments.helpRequested()) {
                System.out.print(CliArguments.usage());
                System.exit(PipelineReport.EXIT_OK);
                return;
            }
            config = new ConfigLoader().load(arguments.overrides());
        } catch (ConfigurationException e) {
            // Exit 2 before anything is started: a misconfigured run must be distinguishable
            // from a run that started and failed.
            System.err.println("configuration error: " + e.getMessage());
            System.err.println("run with --help for the list of settings");
            System.exit(PipelineReport.EXIT_CONFIG);
            return;
        }

        LoggingSupport.configure(config.logLevel());
        LOG.info("starting with " + config);

        int exitCode = run(config);
        LOG.log(Level.FINE, "exiting with code {0}", exitCode);
        // Explicit: any non-daemon thread that outlived the drain would otherwise hang the JVM
        // after main returns, turning a reported failure into a hung CI job.
        System.exit(exitCode);
    }

    /**
     * Builds the graph, runs the pipeline and renders the report.
     *
     * <p>Separate from {@link #main(String[])} and free of {@code System.exit} so an
     * integration test can assert on the exit code without killing the test JVM.
     *
     * @param config validated configuration
     * @return the process exit code
     */
    // "try": the control plane and the dashboard are held only to be closed at the right
    // moment, so never referencing them in the block is the point, not an oversight.
    @SuppressWarnings("try")
    public static int run(PipelineConfig config) {
        if (config.jdbcEnabled()) {
            // Refused here rather than at the end of the run. The JDBC repository is a stub
            // (see JdbcAggregateRepository): wiring it produced a pipeline that generated,
            // filtered and aggregated every event correctly, wrote the CSV, and then failed
            // on save -- reporting FAILED and exit 1 for a run that had in fact done its work.
            // An unimplemented feature must be refused before anything starts, so exit 2
            // means the same thing here as it does for any other unusable configuration.
            System.err.println("configuration error: " + PipelineConfig.KEY_JDBC_URL
                    + " is set, but JDBC persistence is not implemented");
            System.err.println("leave it blank to use the in-memory repository; "
                    + "see migrations/README.md for the schema it would write to");
            return PipelineReport.EXIT_CONFIG;
        }

        AtomicMetricsRecorder metrics = new AtomicMetricsRecorder();
        MessageChannel rawQueue = new BoundedStageQueue(QUEUE_RAW, config.queueCapacity(), metrics);
        MessageChannel filteredQueue = new BoundedStageQueue(QUEUE_FILTERED, config.queueCapacity(), metrics);
        List<MessageChannel> channels = List.of(rawQueue, filteredQueue);

        ThresholdPredicate predicate = new ThresholdPredicate(config.filterThreshold());
        AggregateRepository repository = repositoryFor();
        AggregateSink sink = sinkFor(config);

        // try-with-resources on the pools: if construction of a later stage throws, the
        // already-created threads are still shut down instead of leaking for the JVM's life.
        try (PipelineExecutors executors =
                     new PipelineExecutors(config.consumerThreads(), config.effectiveParallelism())) {
            RateLimiter rateLimiter = TokenBucketRateLimiter.of(config.eventsPerSecond());
            ProducerStage producer = new ProducerStage(
                    new SyntheticSensorEventGenerator(config.randomSeed(), config.sensorCount()),
                    rateLimiter, rawQueue, metrics, config.batchSize(), config.eventCount(),
                    config.maxDuration(), config.poisonPillCount(), executors.producerExecutor());
            FilterStage filter = new FilterStage(rawQueue, filteredQueue, predicate, metrics,
                    executors.consumerExecutor(), config.consumerThreads(), config.pollTimeout());
            AggregationStage aggregation = new AggregationStage(filteredQueue, executors.forkJoinPool(),
                    executors.dispatcherExecutor(), sink, repository, metrics,
                    config.sequentialThreshold(), config.pollTimeout());
            PipelineOrchestrator orchestrator = new PipelineOrchestrator(producer, filter, aggregation,
                    executors, metrics, config.shutdownTimeout());

            CountDownLatch finished = new CountDownLatch(1);
            Thread hook = shutdownHook(orchestrator, finished, config);
            Runtime.getRuntime().addShutdownHook(hook);
            try {
                PipelineReport report;
                // Both optional resources are closed here, before the outer
                // try-with-resources tears down the pools: a request handler or a dashboard
                // refresh must never observe a half-stopped pipeline.
                try (ControlPlaneServer controlPlane =
                             startControlPlane(config, metrics, aggregation, predicate, channels);
                     ConsoleDashboard dashboard = startDashboard(config, metrics, aggregation, channels)) {
                    report = orchestrator.run();
                }
                System.out.println(report.describe());
                return report.exitCode();
            } finally {
                // Release the hook first, then deregister it. Ordering matters: a hook still
                // waiting on the latch would block JVM exit for its whole timeout.
                finished.countDown();
                removeHook(hook);
            }
        } catch (RuntimeException e) {
            // A wiring failure (bad output path, port already bound) is a failed run, not a
            // stack trace on the operator's terminal.
            LOG.log(Level.SEVERE, "pipeline could not run", e);
            System.err.println("pipeline failed: " + e.getMessage());
            return PipelineReport.EXIT_FAILURE;
        }
    }

    /**
     * The only repository this application can wire.
     *
     * <p>There is deliberately no JDBC branch here. {@link JdbcAggregateRepository} exists as
     * a documented seam for the schema in {@code migrations/}, but it is not implemented, and
     * a configured JDBC URL is rejected at the top of {@link #run(PipelineConfig)} — one guard,
     * at startup, instead of a choice here that can only end in a late failure.
     */
    private static AggregateRepository repositoryFor() {
        return new InMemoryAggregateRepository();
    }

    /** Console table plus a CSV report under {@code pipeline.output.dir}. */
    private static AggregateSink sinkFor(PipelineConfig config) {
        return CompositeAggregateSink.of(
                new ConsoleAggregateSink(),
                new CsvAggregateSink(config.outputDir(), CSV_FILE_NAME));
    }

    /**
     * Starts the control plane, or a no-op stand-in when it is disabled.
     *
     * <p>Returning {@code null} would push a null check into the try-with-resources; a
     * stopped {@link ControlPlaneServer} is not an option either, because construction binds
     * the socket. So when disabled this returns {@code null} and the caller's
     * try-with-resources tolerates it — {@code close()} on a {@code null} resource is a
     * no-op by specification, which is exactly the behaviour wanted here.
     */
    private static ControlPlaneServer startControlPlane(PipelineConfig config, AtomicMetricsRecorder metrics,
                                                        AggregationStage aggregation, ThresholdPredicate predicate,
                                                        List<MessageChannel> channels) {
        if (!config.httpEnabled()) {
            return null;
        }
        PipelineControlHandler handler = new PipelineControlHandler(config.httpToken(), metrics,
                aggregation::currentSnapshot, predicate, channels);
        ControlPlaneServer server = new ControlPlaneServer(config.httpPort(), handler);
        server.start();
        return server;
    }

    /** Starts the dashboard, or {@code null} when disabled; see {@link #startControlPlane}. */
    private static ConsoleDashboard startDashboard(PipelineConfig config, AtomicMetricsRecorder metrics,
                                                   AggregationStage aggregation, List<MessageChannel> channels) {
        if (!config.dashboardEnabled()) {
            return null;
        }
        ConsoleDashboard dashboard = new ConsoleDashboard(metrics, aggregation::currentSnapshot,
                channels, config.dashboardInterval());
        dashboard.start();
        return dashboard;
    }

    /**
     * The {@code Ctrl+C} handler: ask every stage to stop, then wait for the report.
     *
     * <p>The wait is what makes the shutdown graceful. It is bounded by the shutdown
     * timeout plus a one-second margin so that a pipeline stuck in an uninterruptible
     * section cannot hold the terminal open indefinitely — at that point the JVM exits and
     * the drain is genuinely lost, which is the correct outcome to make visible rather than
     * hide behind an unbounded wait.
     */
    private static Thread shutdownHook(PipelineOrchestrator orchestrator, CountDownLatch finished,
                                       PipelineConfig config) {
        long waitMillis = config.shutdownTimeout().toMillis() + HOOK_MARGIN_MILLIS;
        return new Thread(() -> {
            LOG.info("shutdown signal received; draining");
            orchestrator.requestStopAll();
            try {
                if (!finished.await(waitMillis, TimeUnit.MILLISECONDS)) {
                    LOG.severe("run did not finish within " + waitMillis + " ms of the shutdown signal; "
                            + "exiting without a report");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "pipeline-shutdown");
    }

    /**
     * Deregisters the hook if the JVM is not already shutting down.
     *
     * <p>{@code removeShutdownHook} throws {@link IllegalStateException} once shutdown has
     * begun — which is precisely the {@code Ctrl+C} path, where the hook is the caller's
     * caller. Ignoring that is correct: the hook has already done its job and the JVM is
     * about to exit. Not removing it on the <em>normal</em> path would leak one hook per
     * {@link #run(PipelineConfig)} call, which matters in tests.
     */
    private static void removeHook(Thread hook) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException e) {
            LOG.log(Level.FINE, "shutdown already in progress; hook left registered");
        }
    }
}
