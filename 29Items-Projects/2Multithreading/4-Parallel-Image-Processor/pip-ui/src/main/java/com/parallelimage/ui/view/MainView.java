package com.parallelimage.ui.view;

import com.parallelimage.ui.UiContext;
import com.parallelimage.ui.i18n.Messages;
import com.parallelimage.ui.viewmodel.BatchViewModel;
import java.util.Objects;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * The window shell: a title strip, the batch screen, and a status bar.
 *
 * <h2>Why the stats poll lives here and not in the view model</h2>
 * {@link com.parallelimage.core.engine.EngineStats#sample} reads six live {@code ForkJoinPool} counters.
 * It is cheap but not free, and — more to the point — there is nothing to <em>subscribe</em> to: the pool
 * does not notify anyone when its active thread count changes. So the numbers have to be pulled, and the
 * pull rate is a presentation decision: 2&nbsp;Hz is fast enough that the status bar looks alive and slow
 * enough to be invisible in a profile. A view model that owned a timer would be a view model that could
 * not be unit-tested without starting the FX toolkit.
 *
 * <p>The {@link Timeline} is stopped in {@link #dispose()}. An {@code INDEFINITE} timeline holds a strong
 * reference to its handler and keeps firing after the window is gone, which on a repeated
 * open-close cycle is a genuine leak rather than a theoretical one.
 *
 * <h2>Why the enhancer description is shown at all</h2>
 * The OpenCV/JNI library is optional and its absence is silent by design — {@code PassthroughEnhancer}
 * takes over and images come out unenhanced. Silent degradation with no indicator is how a user spends
 * an afternoon wondering why {@code enhance:clahe} does nothing. The status bar names whichever
 * implementation actually won the SPI lookup, so the answer is on screen before the question is asked.
 */
public final class MainView extends BorderPane {

    /** 2 Hz. See the class javadoc: fast enough to look live, slow enough to be free. */
    private static final Duration STATS_INTERVAL = Duration.millis(500);

    private final BatchViewModel viewModel;
    private final Messages messages;
    private final BatchProcessorView batchView;
    private final Timeline statsTimer;

    public MainView(UiContext context) {
        Objects.requireNonNull(context, "context");
        this.viewModel = new BatchViewModel(context);
        this.messages = viewModel.messages();
        this.batchView = new BatchProcessorView(viewModel, messages);

        getStyleClass().add("main-view");
        setTop(buildHeader());
        setCenter(batchView);
        setBottom(buildStatusBar());

        this.statsTimer = new Timeline(new KeyFrame(STATS_INTERVAL, event -> viewModel.refreshStats()));
        statsTimer.setCycleCount(Timeline.INDEFINITE);
    }

    /**
     * Starts the progress pump and the stats poll, and loads history once.
     *
     * <p>Separate from the constructor because both of those touch the FX toolkit's animation machinery,
     * and a constructor that starts timers cannot be called from a test that only wants to check the
     * layout. The caller invokes this after the scene is showing.
     */
    public void activate() {
        viewModel.activate();
        viewModel.refreshStats();
        statsTimer.play();
        batchView.loadHistory();
    }

    /** Stops the timers and asks the view model to release its executor. Idempotent. */
    public void dispose() {
        statsTimer.stop();
        viewModel.shutdown();
    }

    private HBox buildHeader() {
        Label title = new Label(messages.get("app.title"));
        title.getStyleClass().add("app-title");

        Label subtitle = new Label(messages.get("mainView.subtitle"));
        subtitle.getStyleClass().add("app-subtitle");

        HBox header = new HBox(8, title, subtitle);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 12, 10, 12));
        header.getStyleClass().add("header");
        return header;
    }

    private HBox buildStatusBar() {
        Label status = new Label();
        status.textProperty().bind(viewModel.statusTextProperty());
        status.getStyleClass().add("status-message");
        status.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
        status.setAccessibleText(messages.get("mainView.status.accessibleText"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        EngineStatsPanel statsPanel = new EngineStatsPanel(viewModel.engineStatsProperty(), messages);

        Label enhancer = new Label();
        enhancer.textProperty().bind(viewModel.statsTextProperty());
        enhancer.getStyleClass().add("status-stats");
        enhancer.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
        enhancer.setAccessibleText(messages.get("mainView.enhancer.accessibleText"));

        HBox bar = new HBox(8, status, new Separator(javafx.geometry.Orientation.VERTICAL), spacer,
                statsPanel, new Separator(javafx.geometry.Orientation.VERTICAL), enhancer);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 12, 6, 12));
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    /** Visible for tests, which drive the view model rather than clicking the buttons. */
    public BatchViewModel viewModel() {
        return viewModel;
    }
}
