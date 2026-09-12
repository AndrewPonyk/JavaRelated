package com.parallelimage.app.cli;

import com.parallelimage.app.i18n.Messages;
import com.parallelimage.app.wiring.ServiceRegistry;
import com.parallelimage.core.io.ImageDiscovery;
import com.parallelimage.core.model.BatchResult;
import com.parallelimage.core.model.ImageJob;
import com.parallelimage.core.model.JobOutcome;
import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.pipeline.PipelineFormat;
import com.parallelimage.core.progress.ProgressEvent;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The headless entry point: parse, plan, process, print, exit.
 *
 * <h2>Exit codes are the API</h2>
 * A CLI in a script is judged entirely by its exit code, so the codes are a contract, listed in
 * {@link CliOptions#usage()} and mirrored in {@code docs/TECH-NOTES.md} §3.3:
 * <ul>
 *   <li><b>0</b> — every job succeeded. From {@link BatchResult#toExitCode()}.</li>
 *   <li><b>1</b> — at least one job failed. The batch still ran to completion; 3 900 of 4 000 images were
 *       written. A script that wants "all or nothing" checks for 0.</li>
 *   <li><b>2</b> — bad usage. Nothing ran, nothing was written, and re-running with corrected arguments
 *       is safe. Distinct from 1 so CI can tell "your pipeline spec has a typo" from "seventeen of your
 *       images are corrupt".</li>
 *   <li><b>3</b> — startup failure: the input directory does not exist, the output directory cannot be
 *       created. Also nothing ran, but the fix is environmental rather than in the command line.</li>
 * </ul>
 * {@code 130} (interrupted) is deliberately not produced: SIGINT is handled by the shutdown hook in
 * {@code Main}, which cancels the batch and lets it report its partial result honestly.
 *
 * <h2>Output discipline</h2>
 * Progress goes to {@code stderr}, results to {@code stdout}. That is what makes
 * {@code pip ... | jq} and {@code pip ... > report.txt} work while a human still sees the counter move.
 * Getting this backwards is why so many tools need a {@code --quiet} flag just to be scriptable.
 */
public final class CliRunner {

    /** Progress is reprinted at most this often. See {@link #maybePrintProgress}. */
    private static final long PRINT_INTERVAL_NANOS = 200_000_000L;

    /** Wide enough to blank the longest progress line this class can emit. */
    private static final int PROGRESS_LINE_WIDTH = 40;

    public static final int EXIT_OK = 0;
    public static final int EXIT_FAILURES = 1;
    public static final int EXIT_USAGE = 2;
    public static final int EXIT_STARTUP = 3;

    private final ServiceRegistry.Registry registry;
    private final PrintStream out;
    private final PrintStream err;
    private final Messages messages;

    private final AtomicInteger printedFailures = new AtomicInteger();
    private volatile long lastPrintNanos;

    /** Defaults to {@link Locale#ROOT} — see {@link #CliRunner(ServiceRegistry.Registry, PrintStream, PrintStream, Messages)}. */
    public CliRunner(ServiceRegistry.Registry registry, PrintStream out, PrintStream err) {
        this(registry, out, err, new Messages(Locale.ROOT));
    }

    public CliRunner(ServiceRegistry.Registry registry, PrintStream out, PrintStream err, Messages messages) {
        this.registry = registry;
        this.out = out;
        this.err = err;
        this.messages = messages;
    }

    /**
     * Runs one batch.
     *
     * @return the process exit code; the caller decides whether to actually call {@code System.exit}
     */
    // NPath is 480 against a threshold of 400, and the number is an artefact of how NPath is defined
    // rather than a description of this method. NPath multiplies the branch counts it meets, which models
    // *nested* conditionals well and a flat guard ladder not at all: this method is four sequential
    // "check one precondition, print one message, return one exit code" steps followed by the batch, and
    // none of the four can be reached with another one's branch still open. That is five outcomes -- four
    // early returns and the success path -- and the rest of what NPath is multiplying are the quiet-mode
    // and singular/plural checks that decide the wording of a printed line and nothing else.
    // Cyclomatic complexity, which
    // does not multiply, sits well inside its threshold and is the metric that would actually notice this
    // method growing a second responsibility. The alternative that satisfies NPath is to hide the guards
    // behind a helper returning an Optional<Integer> exit code, which converts a readable top-to-bottom
    // list of startup failures into a pair of methods that must be read together to see the order things
    // are checked in. The threshold stays where it is; this one method is excused, with its reason.
    @SuppressWarnings("PMD.NPathComplexity")
    public int run(CliOptions options) {
        List<String> problems = options.validate();
        if (!problems.isEmpty()) {
            problems.forEach(problem -> err.println("error: " + problem));
            err.println();
            err.println(messages.get("cli.usageHint"));
            return EXIT_USAGE;
        }

        Path input = options.input().orElseThrow();
        Path output = options.output().orElseThrow();

        if (!Files.isDirectory(input)) {
            err.println("error: " + messages.get("cli.error.notADirectory", input));
            return EXIT_STARTUP;
        }
        try {
            // Created up front rather than lazily per job. 4 000 jobs racing to create the same tree is
            // 4 000 chances to hit a partially-created directory, and the failure would be reported
            // per-image instead of once, at the top, where it belongs.
            Files.createDirectories(output);
        } catch (IOException e) {
            err.println("error: " + messages.get("cli.error.cannotCreateOutput", output, e.getMessage()));
            return EXIT_STARTUP;
        }

        ProcessingOptions processing = options.toProcessingOptions(registry.defaultOptions());
        List<ImageJob> jobs = ImageDiscovery.plan(input, output, processing, options.recursive());

        if (jobs.isEmpty()) {
            // Exit 0, not an error. An empty input directory in a nightly job means "nothing to do
            // tonight", and failing would page somebody for a batch that has no work in it.
            out.println(messages.get("cli.noImagesFound", input)
                    + (options.recursive() ? "" : messages.get("cli.noImagesFound.recursiveHint")));
            return EXIT_OK;
        }

        if (!options.quiet()) {
            String historySuffix = registry.historyEnabled() ? "" : messages.get("cli.processing.historyOff");
            err.println(messages.get(jobs.size() == 1 ? "cli.processing.singular" : "cli.processing.plural",
                    String.valueOf(jobs.size()), String.valueOf(registry.engine().parallelism()), historySuffix));
            err.println(messages.get("cli.processing.pipeline", describePipeline(processing)));
        }

        if (options.dryRun()) {
            out.println(messages.get(jobs.size() == 1 ? "cli.dryRun.header.singular" : "cli.dryRun.header.plural",
                    String.valueOf(jobs.size())));
            jobs.forEach(job -> out.println(messages.get("cli.dryRun.line", job.source(), job.target())));
            return EXIT_OK;
        }

        long startNanos = System.nanoTime();
        BatchResult result = registry.engine().process(jobs,
                options.quiet() ? event -> { } : this::onProgress);
        long elapsedNanos = System.nanoTime() - startNanos;

        if (!options.quiet()) {
            clearProgressLine();
        }
        printSummary(result, jobs.size(), elapsedNanos);
        return result.toExitCode();
    }

    // ------------------------------------------------------------------------
    //  Progress
    // ------------------------------------------------------------------------

    /**
     * Rewrites a single progress line on stderr.
     *
     * <p>Rate-limited to 5 Hz for the same reason the UI's {@code ProgressBridge} coalesces at 30 Hz: this
     * is called from every worker thread on every job boundary, and a 4 000-image batch would otherwise
     * emit 8 000 lines of terminal I/O — which is slow, synchronised, and drowns out anything useful.
     * Lossless because {@code completed} is an absolute count, so a skipped print costs nothing.
     *
     * <p>Failures ignore the rate limit and print on their own lines. They are the output someone will
     * scroll back to find, and a failure that appeared only inside a carriage-returned progress line is a
     * failure nobody ever saw.
     */
    private void onProgress(ProgressEvent event) {
        switch (event.phase()) {
            case JOB_FAILED -> {
                clearProgressLine();
                err.println(messages.get("cli.progress.failed", event.detail()));
                printedFailures.incrementAndGet();
            }
            case JOB_COMPLETED, JOB_STARTED -> maybePrintProgress(event);
            case BATCH_STARTED, BATCH_FINISHED, JOB_CANCELLED -> {
                // BATCH_* add nothing a human needs -- the counts are already in the progress line and the
                // summary. JOB_CANCELLED arrives in bulk after Ctrl-C, and printing 3 000 of them would
                // bury the partial summary that the operator actually wants to read.
            }
        }
    }

    private void maybePrintProgress(ProgressEvent event) {
        long now = System.nanoTime();
        // Deliberately racy: two threads can both decide to print, and the cost of that is one duplicated
        // line on a terminal. A lock here would serialise every worker on every job boundary.
        if (now - lastPrintNanos < PRINT_INTERVAL_NANOS) {
            return;
        }
        lastPrintNanos = now;
        int percent = (int) Math.round(event.fraction() * 100);
        err.print("\r  %3d%%  %d/%d".formatted(percent, event.completed(), event.total()));
        err.flush();
    }

    /**
     * Ends the carriage-returned progress line.
     *
     * <p>Without this the next thing printed lands on top of the progress text and leaves its tail behind
     * — "100% 4000/4000" followed by "ok: 4000" renders as "ok: 4000000/4000".
     *
     * <p>Blanked with spaces rather than the ANSI erase-to-end-of-line sequence. Older {@code cmd.exe}
     * builds render {@code ESC[K} literally, and this project's stated target is local execution on
     * Windows; a portable kludge beats a control code that prints as garbage.
     */
    private void clearProgressLine() {
        err.print("\r" + " ".repeat(PROGRESS_LINE_WIDTH) + "\r");
        err.flush();
    }

    // ------------------------------------------------------------------------
    //  Summary
    // ------------------------------------------------------------------------

    /**
     * Prints the outcome to stdout.
     *
     * <p>Failure detail comes from {@link BatchResult#failures()} rather than from the progress events
     * already printed: the events are best-effort and the listener is a no-op under {@code --quiet},
     * whereas the result is complete by construction. The two are reconciled by only printing the list
     * when the live output did not already show it.
     *
     * <p>Elapsed time is measured around {@code process()} rather than derived from
     * {@link BatchResult#totalNanos()}. That field is the <em>sum</em> of per-job durations across all
     * workers, so on eight threads it is roughly eight times the wall clock; reporting it as elapsed makes
     * the tool look eight times slower than it is, and dividing by the worker count only looks right when
     * the pool is perfectly saturated.
     */
    private void printSummary(BatchResult result, int planned, long elapsedNanos) {
        out.println(messages.get("cli.summary.line1",
                String.valueOf(result.total()), String.valueOf(planned),
                "%.1f".formatted(elapsedNanos / 1_000_000_000.0d),
                "%.1f".formatted(result.averageMillis()),
                "%.1f".formatted(result.pixelsProcessed() / 1_000_000.0d)));
        out.println(messages.get("cli.summary.line2",
                String.valueOf(result.succeeded()), String.valueOf(result.failed()),
                String.valueOf(result.cancelled())));

        List<JobOutcome.Failure> failures = result.failures();
        if (failures.isEmpty()) {
            return;
        }
        if (printedFailures.get() == 0) {
            out.println();
            out.println(messages.get("cli.summary.failuresHeader"));
            failures.forEach(failure -> out.println(messages.get("cli.summary.failureLine",
                    failure.jobId(), failure.exceptionType(), failure.operationName(), failure.reason())));
        }
        if (registry.historyEnabled()) {
            out.println();
            out.println(messages.get("cli.summary.historyHint"));
        }
    }

    private String describePipeline(ProcessingOptions options) {
        if (options.operations().isEmpty()) {
            // Not an error. A pipeline-less run is the standard way to transcode a directory, and saying
            // so is better than printing an empty string that reads like a bug.
            return messages.get("cli.pipeline.none", options.outputFormat());
        }
        return PipelineFormat.render(options.operations());
    }
}
