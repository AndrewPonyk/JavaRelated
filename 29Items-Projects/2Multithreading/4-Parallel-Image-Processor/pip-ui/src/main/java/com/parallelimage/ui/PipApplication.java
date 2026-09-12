package com.parallelimage.ui;

import com.parallelimage.core.port.JobRepository;
import com.parallelimage.ui.view.MainView;
import com.parallelimage.ui.viewmodel.BatchViewModel;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.Objects;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * The JavaFX {@link Application}. Owns the window and nothing else.
 *
 * <h2>The one static field, and why it is not a service locator</h2>
 * JavaFX constructs this class itself, reflectively, through its no-argument constructor. There is no
 * seam: {@code launch()} takes a {@code Class}, not an instance, so a constructor argument is not an
 * option and dependency injection has to happen out of band.
 *
 * <p>{@link #prepare(UiContext)} is that out-of-band channel, reduced to the smallest thing that works:
 * one field, written exactly once by {@code pip-app} before {@code launch()}, read exactly once inside
 * {@link #start(Stage)}, and never consulted again. From {@code start()} onward the context travels down
 * the view tree as a normal constructor argument, so no view or view model has a static dependency on
 * anything.
 *
 * <p>The distinction matters. A service locator is read from everywhere and therefore cannot be varied
 * per test; this is a single assignment on the way in. The field is {@code volatile} because the writer
 * and the FX thread are different threads and there is no other happens-before edge between
 * {@code prepare} and {@code start} that the JLS actually guarantees.
 *
 * <h2>Why {@code start()} refuses to invent a context</h2>
 * If {@code prepare} was not called, {@code start()} throws. The tempting alternative — build a default
 * engine and a {@link JobRepository#NO_OP} on the spot — would produce a window that opens, works, and
 * writes no history, and the bug report would be "history is broken" rather than "the launcher is
 * misconfigured". Failing at startup names the actual fault.
 */
public final class PipApplication extends Application {

    private static final Logger LOG = System.getLogger(PipApplication.class.getName());

    private static final String STYLESHEET = "/com/parallelimage/ui/styles/app.css";

    /** See the class javadoc. Volatile: written by the launcher thread, read by the FX thread. */
    private static volatile UiContext pending;

    private MainView mainView;

    /**
     * Hands the application its collaborators. Call before {@link Application#launch}.
     *
     * <p>Not idempotent-safe by accident: calling it twice replaces the context, which is fine before
     * launch and useless after. It is not synchronized because the only legal call site is single-
     * threaded startup code.
     */
    public static void prepare(UiContext context) {
        pending = Objects.requireNonNull(context, "context");
    }

    @Override
    public void start(Stage stage) {
        UiContext context = pending;
        if (context == null) {
            throw new IllegalStateException(
                    "PipApplication.prepare(UiContext) was not called before launch(); "
                            + "start pip-app's UiLauncher rather than this class directly");
        }

        mainView = new MainView(context);
        Scene scene = new Scene(mainView, 1024, 720);
        applyStylesheet(scene);
        UiPreferences preferences = new UiPreferences();

        stage.setTitle(mainView.viewModel().messages().get("app.title"));
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(520);
        applySavedGeometry(stage, preferences);
        applySavedDirectories(mainView.viewModel(), preferences);

        // setOnCloseRequest, not stop(): the window can be closed while a batch is running, and the
        // cancellation request wants to be in flight before the FX toolkit starts tearing down. stop()
        // then runs as well, which is why dispose() is idempotent. Preferences are saved here rather
        // than in stop() for the same reason: stop() has no Stage to read geometry back from.
        stage.setOnCloseRequest(event -> {
            savePreferences(stage, mainView.viewModel(), preferences);
            mainView.dispose();
        });

        stage.show();
        // After show(), so the first stats poll and history query happen against a live scene graph
        // rather than queueing work behind the initial layout pass.
        mainView.activate();

        LOG.log(Level.INFO, () -> "UI started with " + context.enhancerDescription());
    }

    /**
     * Restores the last saved position and size, but only if it still lands on a connected monitor.
     * A geometry saved from a display that has since been unplugged or resized must not put the window
     * somewhere the user cannot see or reach it.
     */
    private static void applySavedGeometry(Stage stage, UiPreferences preferences) {
        preferences.loadGeometry().filter(PipApplication::isOnScreen).ifPresent(geometry -> {
            stage.setX(geometry.x());
            stage.setY(geometry.y());
            stage.setWidth(geometry.width());
            stage.setHeight(geometry.height());
        });
    }

    private static boolean isOnScreen(UiPreferences.Geometry geometry) {
        Rectangle2D bounds = new Rectangle2D(geometry.x(), geometry.y(), geometry.width(), geometry.height());
        return Screen.getScreens().stream().anyMatch(screen -> screen.getBounds().intersects(bounds));
    }

    private static void applySavedDirectories(BatchViewModel viewModel, UiPreferences preferences) {
        preferences.lastInputDirectory().ifPresent(viewModel.inputDirectoryProperty()::set);
        preferences.lastOutputDirectory().ifPresent(viewModel.outputDirectoryProperty()::set);
    }

    private static void savePreferences(Stage stage, BatchViewModel viewModel, UiPreferences preferences) {
        UiPreferences.Geometry geometry =
                new UiPreferences.Geometry(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        preferences.save(geometry, viewModel.inputDirectoryProperty().get(),
                viewModel.outputDirectoryProperty().get());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Deliberately does not close the engine or the repository. {@code pip-app} created them and
     * registered a shutdown hook for them; closing a {@code ForkJoinPool} from here would leave the CLI
     * path — which shares the wiring code — shutting down a pool it still owns.
     */
    @Override
    public void stop() {
        if (mainView != null) {
            mainView.dispose();
        }
    }

    /**
     * Attaches {@code app.css} if it is on the classpath.
     *
     * <p>A missing stylesheet is a warning, not a failure. {@code Scene#getStylesheets} takes a
     * {@code String} URL and silently ignores one it cannot resolve, so the resource is looked up first
     * — otherwise a packaging mistake produces an unstyled window with no explanation anywhere.
     */
    private void applyStylesheet(Scene scene) {
        URL css = PipApplication.class.getResource(STYLESHEET);
        if (css == null) {
            LOG.log(Level.WARNING, () -> "stylesheet " + STYLESHEET + " not on the classpath; using defaults");
            return;
        }
        scene.getStylesheets().add(css.toExternalForm());
    }
}
