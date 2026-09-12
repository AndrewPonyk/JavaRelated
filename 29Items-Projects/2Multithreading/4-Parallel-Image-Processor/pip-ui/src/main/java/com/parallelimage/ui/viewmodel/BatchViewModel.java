package com.parallelimage.ui.viewmodel;

import com.parallelimage.core.engine.EngineStats;
import com.parallelimage.core.io.ImageDiscovery;
import com.parallelimage.core.model.BatchResult;
import com.parallelimage.core.model.ImageJob;
import com.parallelimage.core.model.JobOutcome;
import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.pipeline.ImageOperation;
import com.parallelimage.core.pipeline.PipelineFormat;
import com.parallelimage.core.port.JobRepository;
import com.parallelimage.core.progress.ProgressEvent;
import com.parallelimage.ui.UiContext;
import com.parallelimage.ui.i18n.Messages;
import com.parallelimage.ui.task.ProgressBridge;
import com.parallelimage.ui.task.RepositoryQueryTask;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * All of the batch screen's state and behaviour, with no reference to a single JavaFX control.
 *
 * <h2>Why a view model rather than logic in the view</h2>
 * The interesting behaviour on this screen is not layout, it is <em>concurrency</em>: a blocking
 * {@code engine.process} call, a filesystem walk, a cancellation flag, progress events arriving from N
 * worker threads, and a state machine that must never show a progress bar and an error message at the
 * same time. Bury that in a {@code Button} handler and it can only be exercised by clicking, on a
 * machine with a display.
 *
 * <p>Here it can be driven directly: construct one, set {@link #inputDirectoryProperty()}, call
 * {@link #start()}, and assert on {@link #stateProperty()} and {@link #progressProperty()}. The view
 * becomes a set of bindings, which is the part that genuinely does need eyes on it.
 *
 * <h2>Which thread does what</h2>
 * <ul>
 *   <li><b>FX thread</b> — every property read and write, without exception. JavaFX properties are not
 *       thread-safe and a background write is the classic source of a UI that is subtly wrong once an
 *       hour rather than reliably broken.</li>
 *   <li><b>{@code pip-ui-batch} (one daemon thread)</b> — the filesystem walk and the blocking
 *       {@code engine.process} call. Single-threaded on purpose: two concurrent batches would share one
 *       {@link com.parallelimage.core.engine.ImageProcessingEngine} cancellation token, so "Cancel"
 *       would stop an arbitrary one of them.</li>
 *   <li><b>fork/join workers</b> — raise progress events into {@link ProgressBridge}, which is the only
 *       thing they touch here.</li>
 * </ul>
 *
 * <h2>The state machine</h2>
 * {@link State} is deliberately explicit rather than derived from a handful of booleans. Three
 * independent flags describe eight states, six of which are nonsense ("running and errored"), and
 * every one of them eventually renders. One enum makes the illegal combinations unrepresentable and
 * lets the view be a single {@code switch}.
 */
public final class BatchViewModel {

    private static final Logger LOG = System.getLogger(BatchViewModel.class.getName());

    /** How many history rows the table shows. Enough to scroll, small enough to query in one hop. */
    private static final int HISTORY_LIMIT = 200;

    /**
     * The one screen state.
     *
     * <p>{@link #EMPTY} is separate from {@link #LOADED} because "the batch ran and produced nothing"
     * and "the batch ran and produced 4 000 files" want completely different screens, and an empty
     * table with no explanation is the single most common way a working application looks broken.
     */
    public enum State {
        /** Nothing has run yet. Controls enabled, no progress bar. */
        IDLE,
        /** Planning or processing. Inputs disabled, progress bar live, Cancel enabled. */
        LOADING,
        /** Finished with at least one result — including a partially failed batch. */
        LOADED,
        /** Finished, but the input directory matched no images. */
        EMPTY,
        /** Could not run at all: unparseable pipeline, unreadable directory, engine fault. */
        ERROR
    }

    private final UiContext context;
    private final Messages messages;
    private final ProgressBridge bridge;

    /**
     * Runs the blocking work. Daemon, because a batch in flight must not prevent JVM exit after the
     * window closes — {@link #shutdown()} asks the engine to cancel, and a worker that has not noticed
     * yet is finishing an image whose output nobody will look at.
     */
    private final ExecutorService batchExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pip-ui-batch");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Guards against a second batch being submitted before the first finishes.
     *
     * <p>The {@link State} enum would seem to be enough, but it lives on the FX thread and is set
     * asynchronously; between {@link #start()} returning and the {@code LOADING} transition being
     * applied there is a window in which a fast double-click submits twice. This closes it.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    // ---- inputs (read/write, bound bidirectionally to controls) --------------

    private final ObjectProperty<Path> inputDirectory = new SimpleObjectProperty<>(this, "inputDirectory");
    private final ObjectProperty<Path> outputDirectory = new SimpleObjectProperty<>(this, "outputDirectory");
    private final StringProperty pipelineText = new SimpleStringProperty(this, "pipelineText", "");
    private final StringProperty outputFormat = new SimpleStringProperty(this, "outputFormat", "png");
    private final BooleanProperty recursive = new SimpleBooleanProperty(this, "recursive", true);
    private final BooleanProperty overwriteExisting = new SimpleBooleanProperty(this, "overwriteExisting", false);

    // ---- outputs (read-only to the view) ------------------------------------

    private final ObjectProperty<State> state = new SimpleObjectProperty<>(this, "state", State.IDLE);
    private final DoubleProperty progress = new SimpleDoubleProperty(this, "progress", 0.0d);
    private final StringProperty statusText = new SimpleStringProperty(this, "statusText", "");
    private final StringProperty errorText = new SimpleStringProperty(this, "errorText", "");
    private final StringProperty statsText = new SimpleStringProperty(this, "statsText", "");
    private final ObjectProperty<EngineStats> engineStats =
            new SimpleObjectProperty<>(this, "engineStats", EngineStats.UNAVAILABLE);
    private final BooleanProperty cancellable = new SimpleBooleanProperty(this, "cancellable", false);

    /** Failure lines for the current batch, newest last. Bound directly to a {@code ListView}. */
    private final ObservableList<String> failures = FXCollections.observableArrayList();

    /** History rows. Replaced wholesale by {@link #refreshHistory()} rather than mutated in place. */
    private final ObservableList<JobRepository.JobRecord> history = FXCollections.observableArrayList();

    public BatchViewModel(UiContext context) {
        this.context = Objects.requireNonNull(context, "context");
        this.messages = new Messages(context.locale());
        this.bridge = new ProgressBridge(this::applyProgress);

        ProcessingOptions defaults = context.defaultOptions();
        outputFormat.set(defaults.outputFormat());
        overwriteExisting.set(defaults.overwriteExisting());
        // Render the configured defaults back into the text box. The operator set them in
        // config/application.properties; showing an empty field would imply they had not.
        pipelineText.set(PipelineFormat.render(defaults.operations()));
        statusText.set(messages.get("vm.status.chooseInput"));
    }

    /** Shared with the view tree so every {@code pip-ui} class loads the bundle exactly once. */
    public Messages messages() {
        return messages;
    }

    /** Must be called on the FX thread once the scene is showing; starts the progress pump. */
    public void activate() {
        bridge.start();
        statsText.set(context.enhancerDescription());
    }

    // ------------------------------------------------------------------------
    //  Commands
    // ------------------------------------------------------------------------

    /**
     * Plans and runs a batch. Returns immediately; watch {@link #stateProperty()}.
     *
     * <p>Validation happens here, on the FX thread, and deliberately <em>before</em> anything is
     * submitted: a mistyped pipeline should turn into a message under the text field in the same frame
     * as the click, not after a two-second directory walk. Only the work that genuinely needs a disk —
     * enumerating the input tree — moves to the background.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            LOG.log(Level.DEBUG, "start() ignored: a batch is already running");
            return;
        }

        Path input = inputDirectory.get();
        Path output = outputDirectory.get();
        ProcessingOptions options;
        try {
            options = buildOptions(input, output);
        } catch (RuntimeException e) {
            running.set(false);
            fail(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return;
        }

        failures.clear();
        errorText.set("");
        progress.set(0.0d);
        state.set(State.LOADING);
        cancellable.set(true);
        statusText.set(messages.get("vm.status.scanning", input));

        String batchId = UUID.randomUUID().toString();
        batchExecutor.execute(() -> runBatch(batchId, input, output, options));
    }

    /**
     * Requests cancellation.
     *
     * <p>Does not wait, and does not move to a terminal state: the batch itself reports
     * {@code BATCH_FINISHED} when the workers actually stop, and pretending otherwise would leave the
     * UI claiming "cancelled" while files were still being written.
     */
    public void cancel() {
        if (context.engine().cancel()) {
            statusText.set(messages.get("vm.status.cancelling"));
            cancellable.set(false);
        }
    }

    /**
     * Reloads the history table off the FX thread.
     *
     * <p>A failed query sets {@link #errorTextProperty()} but does <em>not</em> move the screen to
     * {@link State#ERROR}: history is auxiliary, and a broken database must not hide a batch that
     * completed successfully. That distinction is the same one {@link JobRepository} makes when it
     * promises never to throw into the engine.
     */
    public void refreshHistory() {
        RepositoryQueryTask<List<JobRepository.JobRecord>> task =
                RepositoryQueryTask.recentJobs(context.repository(), HISTORY_LIMIT);
        task.setOnSucceeded(event -> history.setAll(task.getValue()));
        task.setOnFailed(event -> {
            errorText.set(messages.get("vm.history.unavailable", task.failureMessage()));
            history.clear();
        });
    }

    /**
     * Polls the pool counters for the status bar. Called from a low-frequency FX timer, never per
     * image.
     *
     * <p>{@code statsText} now carries only the enhancer description — the numeric pool counters
     * that used to be concatenated into it live in {@link #engineStatsProperty()} instead, one
     * field at a time, for {@code EngineStatsPanel}.
     */
    public void refreshStats() {
        engineStats.set(context.engine().stats());
        statsText.set(context.enhancerDescription());
    }

    /**
     * Releases UI-side resources. Does not close the engine or the repository — {@link UiContext} was
     * handed those from outside, and whoever created them closes them.
     */
    public void shutdown() {
        bridge.stop();
        context.engine().cancel();
        batchExecutor.shutdownNow();
    }

    // ------------------------------------------------------------------------
    //  Background body
    // ------------------------------------------------------------------------

    private void runBatch(String batchId, Path input, Path output, ProcessingOptions options) {
        try {
            List<ImageJob> planned = ImageDiscovery.plan(input, output, options, recursive.get());
            if (planned.isEmpty()) {
                onFx(() -> {
                    state.set(State.EMPTY);
                    cancellable.set(false);
                    statusText.set(messages.get("vm.status.noImagesFound", input));
                });
                return;
            }

            // plan() mints its own batch id, and the engine reads it from the first job -- so the id
            // generated in start() is only ever a log correlation handle. Deliberately not "fixed" by
            // rewriting the jobs: two sources of truth for a batch id is worse than one unused string.
            onFx(() -> statusText.set(messages.get("vm.status.processingCount", String.valueOf(planned.size()))));

            BatchResult result = context.engine().process(planned, bridge);
            onFx(() -> finish(result, planned.size()));
        } catch (RuntimeException e) {
            // A RuntimeException here is a *setup* fault -- an unreadable directory, a pipeline the
            // engine rejected. Per-image faults never reach this point; they are Failure records inside
            // the BatchResult, which is the whole reason this catch does not need to inspect the cause.
            LOG.log(Level.WARNING, () -> "batch " + batchId + " could not run", e);
            onFx(() -> fail(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            running.set(false);
        }
    }

    private void finish(BatchResult result, int planned) {
        cancellable.set(false);
        progress.set(1.0d);
        state.set(result.isEmpty() ? State.EMPTY : State.LOADED);
        statusText.set(messages.get("vm.status.finished",
                String.valueOf(result.succeeded()), String.valueOf(result.failed()), String.valueOf(result.cancelled()),
                String.valueOf(result.total()), String.valueOf(planned),
                "%.1f".formatted(result.totalNanos() / 1_000_000_000.0d)));
        // The failure list is authoritative here even though progress events also reported failures:
        // the bridge may have dropped some under load, and BatchResult never does.
        failures.setAll(result.failures().stream().map(this::describe).toList());
        long dropped = bridge.droppedFailures();
        if (dropped > 0L) {
            LOG.log(Level.INFO, () -> dropped + " progress failure notices were coalesced away");
        }
        refreshHistory();
    }

    private void fail(String message) {
        state.set(State.ERROR);
        cancellable.set(false);
        progress.set(0.0d);
        errorText.set(message);
        statusText.set(messages.get("vm.status.couldNotStart"));
    }

    // ------------------------------------------------------------------------
    //  Progress
    // ------------------------------------------------------------------------

    /**
     * Applies one coalesced event. Always on the FX thread — {@link ProgressBridge} guarantees it.
     *
     * <p>{@code BATCH_FINISHED} intentionally does <em>not</em> set a terminal state. The authoritative
     * numbers are in the {@link BatchResult} that {@code process} returns a moment later, and letting
     * the event win would briefly show "0 failed" for a batch that failed.
     */
    private void applyProgress(ProgressEvent event) {
        switch (event.phase()) {
            case BATCH_STARTED -> {
                progress.set(0.0d);
                statusText.set(messages.get("vm.status.processingCount", String.valueOf(event.total())));
            }
            case JOB_STARTED, JOB_COMPLETED -> {
                progress.set(event.fraction());
                statusText.set(messages.get("vm.status.progress", String.valueOf(event.completed()),
                        String.valueOf(event.total()), "%.0f".formatted(event.fraction() * 100.0d)));
            }
            case JOB_FAILED -> {
                progress.set(event.fraction());
                failures.add(event.detail());
            }
            case JOB_CANCELLED -> progress.set(event.fraction());
            case BATCH_FINISHED -> progress.set(event.fraction());
        }
    }

    private String describe(JobOutcome.Failure failure) {
        String where = failure.operationName() == null || failure.operationName().isBlank()
                ? "" : messages.get("vm.failure.where", failure.operationName());
        return messages.get("vm.failure.line", failure.exceptionType(), where, failure.reason());
    }

    // ------------------------------------------------------------------------
    //  Options
    // ------------------------------------------------------------------------

    /**
     * Turns the form into a {@link ProcessingOptions}, throwing a message fit to show a user.
     *
     * <p>The thresholds and the pixel cap come from {@link UiContext#defaultOptions()} rather than from
     * {@link ProcessingOptions#defaults()}: those are tuning and safety settings the operator set in
     * {@code config/application.properties}, and a UI that silently reset them would make the config
     * file a lie.
     */
    private ProcessingOptions buildOptions(Path input, Path output) {
        if (input == null) {
            throw new IllegalStateException(messages.get("vm.error.chooseInput"));
        }
        if (output == null) {
            throw new IllegalStateException(messages.get("vm.error.chooseOutput"));
        }
        if (input.equals(output)) {
            // The engine rejects source == target per job anyway; catching it here names the actual
            // mistake instead of failing 4 000 times with a per-file message.
            throw new IllegalStateException(messages.get("vm.error.sameFolder"));
        }

        List<ImageOperation> operations;
        try {
            operations = PipelineFormat.parse(pipelineText.get());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(messages.get("vm.error.pipeline", e.getMessage()), e);
        }

        ProcessingOptions defaults = context.defaultOptions();
        return ProcessingOptions.builder()
                .operations(operations)
                .outputFormat(outputFormat.get())
                .quality(defaults.quality())
                .tileThresholdPixels(defaults.tileThresholdPixels())
                .batchThresholdJobs(defaults.batchThresholdJobs())
                .stripMetadata(defaults.stripMetadata())
                .overwriteExisting(overwriteExisting.get())
                .maxPixelsPerImage(defaults.maxPixelsPerImage())
                .build();
    }

    private static void onFx(Runnable action) {
        ProgressBridge.onFxThread(action);
    }

    // ------------------------------------------------------------------------
    //  Properties
    // ------------------------------------------------------------------------

    public ObjectProperty<Path> inputDirectoryProperty() {
        return inputDirectory;
    }

    public ObjectProperty<Path> outputDirectoryProperty() {
        return outputDirectory;
    }

    public StringProperty pipelineTextProperty() {
        return pipelineText;
    }

    public StringProperty outputFormatProperty() {
        return outputFormat;
    }

    public BooleanProperty recursiveProperty() {
        return recursive;
    }

    public BooleanProperty overwriteExistingProperty() {
        return overwriteExisting;
    }

    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    public ReadOnlyDoubleProperty progressProperty() {
        return progress;
    }

    public ReadOnlyStringProperty statusTextProperty() {
        return statusText;
    }

    public ReadOnlyStringProperty errorTextProperty() {
        return errorText;
    }

    public ReadOnlyStringProperty statsTextProperty() {
        return statsText;
    }

    /** Individual engine/pool counters for {@code EngineStatsPanel}, refreshed alongside {@link #statsTextProperty()}. */
    public ReadOnlyObjectProperty<EngineStats> engineStatsProperty() {
        return engineStats;
    }

    public ReadOnlyBooleanProperty cancellableProperty() {
        return cancellable;
    }

    public ObservableList<String> failures() {
        return failures;
    }

    public ObservableList<JobRepository.JobRecord> history() {
        return history;
    }

    /** Visible for tests: the bridge's diagnostics are the only view onto coalescing. */
    public ProgressBridge progressBridge() {
        return bridge;
    }
}
