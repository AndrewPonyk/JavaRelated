package com.parallelimage.ui;

import javafx.application.Application;

/**
 * Starts the UI. Deliberately does <strong>not</strong> extend {@link Application}.
 *
 * <h2>The error this class exists to prevent</h2>
 * <pre>
 * Error: JavaFX runtime components are missing, and are required to run this application
 * </pre>
 * That message appears when the main class <em>is</em> an {@code Application} subclass and JavaFX is on
 * the class path rather than the module path. The JavaFX launcher checks, from
 * {@code LauncherHelper}, whether the JavaFX modules were resolved as modules; if the main class extends
 * {@code Application} and they were not, it refuses to start — before any of your code runs, so there is
 * nothing to catch and nothing to log.
 *
 * <p>When the main class is an ordinary class, that check does not apply. It calls
 * {@code Application.launch(PipApplication.class, args)} itself, the toolkit initialises from the class
 * path, and the application starts. The indirection is one line and it is the reason
 * {@code java -cp pip-app.jar com.parallelimage.ui.UiLauncher} works at all.
 *
 * <p>This project builds a non-modular jar on purpose (there is no {@code module-info.java}; see
 * TECH-NOTES §3.3 on {@code jpackage --main-jar}), so this is the normal path, not a fallback.
 *
 * <h2>Why it is here and not in pip-app</h2>
 * The trick belongs next to the {@code Application} it launches, so that anyone deleting
 * {@link PipApplication} finds this class in the same package and understands why it is not merged into
 * it. {@code pip-app} still owns wiring: it builds the {@link UiContext}, calls
 * {@link PipApplication#prepare}, and only then hands control here.
 */
public final class UiLauncher {

    private UiLauncher() {
    }

    /**
     * Blocks until the last window closes.
     *
     * <p>{@code Application.launch} may only be called once per JVM and throws
     * {@link IllegalStateException} on a second call — which is why this is a plain static method and not
     * something a caller might reasonably invoke in a loop.
     *
     * @param context collaborators for the view tree; must be non-null
     * @param args    forwarded to JavaFX, which reads {@code -Dprism.*} style parameters from them
     */
    public static void launch(UiContext context, String... args) {
        PipApplication.prepare(context);
        Application.launch(PipApplication.class, args);
    }

    /**
     * Entry point for the rare case of running the UI module without {@code pip-app}'s wiring.
     *
     * <p>It fails, on purpose, with a message that says where to look. Building a default engine here
     * would mean {@code pip-ui} needed a persistence implementation to construct a repository — the exact
     * dependency the hexagonal layout exists to prevent (see {@link UiContext}).
     */
    public static void main(String[] args) {
        throw new UnsupportedOperationException(
                "pip-ui cannot wire itself: it has no persistence dependency by design. "
                        + "Run com.parallelimage.app.Main --ui, or call UiLauncher.launch(UiContext).");
    }
}
