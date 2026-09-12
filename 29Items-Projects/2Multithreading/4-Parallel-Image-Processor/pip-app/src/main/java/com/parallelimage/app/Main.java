package com.parallelimage.app;

import com.parallelimage.app.api.BatchJobController;
import com.parallelimage.app.cli.CliOptions;
import com.parallelimage.app.cli.CliRunner;
import com.parallelimage.app.config.AppConfig;
import com.parallelimage.app.i18n.Messages;
import com.parallelimage.app.logging.JsonLogHandler;
import com.parallelimage.app.wiring.ServiceRegistry;
import com.parallelimage.core.fork.ForkJoinConfig;
import com.parallelimage.ui.UiContext;
import com.parallelimage.ui.UiLauncher;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;

/**
 * The single entry point. Chooses a mode, wires the application once, runs it, and exits.
 *
 * <h2>Three modes, one wiring</h2>
 * <pre>
 * pip --in DIR --out DIR [options]   batch, headless          (default)
 * pip --ui                           JavaFX window
 * pip --serve                        local control API, then block
 * </pre>
 * The mode flags are stripped here rather than parsed by {@link CliOptions} because they select which
 * object graph runs, not how a batch behaves — and because {@code --ui} takes none of the batch options,
 * so letting the CLI parser see it would mean {@code --in} was "required except when it is not".
 *
 * <h2>Exit codes</h2>
 * Only the batch mode produces a meaningful one; see {@link CliRunner}. {@code --ui} and {@code --serve}
 * exit 0 unless startup failed, because "the operator closed the window" is not an error and a supervisor
 * restarting the API on a non-zero exit needs that exit to mean something.
 *
 * <h2>Why {@code System.exit} is called explicitly</h2>
 * A {@code ForkJoinPool}'s workers are daemon threads, but the JavaFX toolkit and the HTTP server both keep
 * non-daemon threads alive; returning from {@code main} would leave the JVM running after the work was
 * done. Calling {@code exit} also runs the shutdown hook, which is where cancellation and close live, so
 * there is exactly one path out of the process regardless of mode.
 */
public final class Main {

    private static final Logger LOG = System.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) {
        List<String> remaining = new ArrayList<>(Arrays.asList(args));
        boolean ui = remaining.remove("--ui");
        boolean serve = remaining.remove("--serve");

        if (ui && serve) {
            // Not silently preferring one: the operator asked for two things and got neither, and guessing
            // which they meant is how a scheduled task ends up opening a window on a headless machine.
            System.err.println("error: --ui and --serve are mutually exclusive");
            System.exit(CliRunner.EXIT_USAGE);
        }

        // Parsed before anything is wired: --help and --version must work when the database is corrupt, the
        // config file is unreadable, or the OpenCV library is a broken symlink. Wiring first would make the
        // one command that tells you what is wrong the command that cannot run.
        CliOptions options;
        try {
            options = CliOptions.parse(remaining.toArray(String[]::new));
        } catch (CliOptions.CliSyntaxException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(CliRunner.EXIT_USAGE);
            return;
        }

        AppConfig config = AppConfig.load();
        installLogHandlerIfConfigured(config);

        if (options.help()) {
            System.out.print(CliOptions.usage(config.locale()));
            System.exit(CliRunner.EXIT_OK);
        }

        if (options.version()) {
            System.out.print(versionReport(config));
            System.exit(CliRunner.EXIT_OK);
        }

        int code = run(config, options, ui, serve);
        System.exit(code);
    }

    /**
     * Opens the registry, dispatches, and closes it exactly once.
     *
     * <h2>Two paths out, one close</h2>
     * A normal return unwinds the stack; Ctrl-C does not unwind it at all, so a batch interrupted by SIGINT
     * would leave the pool running and the SQLite file mid-transaction unless a shutdown hook closed things
     * too. Both are therefore wired: the {@code finally} for the ordinary case, the hook for the signal.
     *
     * <p>They share one {@link AtomicBoolean} because {@code Registry#close} guards each step individually
     * but is not idempotent — calling it twice would shut the pool down twice and log a warning about a
     * connection that is already closed. The guard makes "whichever path gets there first wins" explicit
     * instead of relying on every downstream {@code close()} tolerating a second call.
     */
    private static int run(AppConfig config, CliOptions options, boolean ui, boolean serve) {
        ServiceRegistry.Registry registry;
        try {
            registry = ServiceRegistry.open(config);
        } catch (RuntimeException e) {
            LOG.log(Level.ERROR, "startup failed", e);
            System.err.println("error: " + e);
            return CliRunner.EXIT_STARTUP;
        }

        AtomicBoolean closed = new AtomicBoolean();
        Runnable closeOnce = () -> {
            if (closed.compareAndSet(false, true)) {
                registry.close();
            }
        };
        Runtime.getRuntime().addShutdownHook(new Thread(closeOnce, "pip-shutdown"));

        try {
            if (ui) {
                return runUi(registry, config);
            }
            if (serve) {
                return runServer(registry);
            }
            return new CliRunner(registry, System.out, System.err, new Messages(config.locale())).run(options);
        } catch (RuntimeException e) {
            // Structural failures only. A failure inside a batch is already an outcome by the time it gets
            // here, and CliRunner has turned it into an exit code -- so this is a pool that refused work,
            // an FX toolkit that could not initialise, or a bug.
            LOG.log(Level.ERROR, "run failed", e);
            System.err.println("error: " + e);
            return CliRunner.EXIT_STARTUP;
        } finally {
            closeOnce.run();
        }
    }

    /**
     * Launches the window and blocks until it closes.
     *
     * <p>SPI discovery and the migration have already happened on this thread inside
     * {@code ServiceRegistry.open}. Doing either lazily from a JavaFX event handler would put a classpath
     * scan and a schema write on the FX application thread, where anything slow freezes the window.
     */
    private static int runUi(ServiceRegistry.Registry registry, AppConfig config) {
        UiContext context = new UiContext(
                registry.engine(),
                registry.repository(),
                registry.defaultOptions(),
                registry.enhancerDescription(),
                config.locale());
        UiLauncher.launch(context);
        return CliRunner.EXIT_OK;
    }

    /**
     * Starts the control API and blocks.
     *
     * <p>{@code api.enabled} is not consulted: {@code --serve} <em>is</em> the request to enable it, and a
     * flag that silently does nothing because a properties file says {@code false} is worse than either
     * behaviour on its own. The property governs whether the API comes up alongside {@code --ui}, which is
     * a different question — see {@code config/application.properties}.
     */
    private static int runServer(ServiceRegistry.Registry registry) {
        try (BatchJobController api = BatchJobController.start(registry)) {
            System.out.println("listening on http://127.0.0.1:" + api.port()
                    + "  (Ctrl-C to stop; POST /batches to submit)");
            api.awaitShutdown();
            return CliRunner.EXIT_OK;
        } catch (IOException e) {
            // Almost always "address already in use", i.e. a second instance. Named explicitly because the
            // raw BindException message does not mention the port.
            System.err.println("error: cannot listen on port " + registry.config().apiPort()
                    + ": " + e.getMessage());
            return CliRunner.EXIT_STARTUP;
        }
    }

    /**
     * Swaps the root logger's handlers for a single {@link JsonLogHandler} when {@code log.format}
     * (env {@code PIP_LOG_FORMAT}) is {@code "json"}. Installed as early as {@link AppConfig} is
     * available so nothing logged during the rest of startup uses the wrong format.
     *
     * <p>The default {@link java.util.logging.ConsoleHandler} is removed rather than left in place
     * alongside the new one: with both attached, every record would be printed twice, once as text
     * and once as JSON, on the same {@code stdout}.
     */
    private static void installLogHandlerIfConfigured(AppConfig config) {
        if (!"json".equalsIgnoreCase(config.logFormat())) {
            return;
        }
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        root.addHandler(new JsonLogHandler());
    }

    /**
     * {@code --version} output.
     *
     * <p>Deliberately more than a version string. Every field here is one that changes what the tool does
     * and cannot be inferred from the command line: the collector actually in use, the worker count after
     * {@code PIP_PARALLELISM} and the {@code availableProcessors()} fallback have been applied, whether the
     * native enhancer loaded, and where history is being written. This is the output to paste into a bug
     * report, which is why it is a block of {@code key: value} lines and not prose.
     *
     * <p>It runs <em>before</em> the registry opens, so it reports the configuration rather than the
     * resolved state — a database path that cannot be opened still appears here, and that is the point:
     * seeing the path the tool intends to use is how you find out it is the wrong one.
     *
     * <p>ASCII only, deliberately. {@code System.out} on Windows encodes with the console codepage, not
     * with {@code file.encoding}, so an em dash in this block prints as {@code ?} in {@code cmd.exe} — in
     * the one command whose whole purpose is to be pasted into a bug report verbatim. Every string this
     * application prints follows the same rule; the javadoc, which is read as UTF-8 source, does not.
     */
    private static String versionReport(AppConfig config) {
        return """
                Parallel Image Processor %s
                  java:         %s (%s)
                  vm:           %s %s
                  collector:    %s
                  os:           %s %s (%s)
                  processors:   %d
                  parallelism:  %d
                  history:      %s
                  control api:  %s
                """.formatted(
                version(),
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("java.vm.name"),
                System.getProperty("java.vm.version"),
                ForkJoinConfig.isShenandoahActive()
                        ? "Shenandoah (configured)"
                        : "not Shenandoah; add -XX:+UseShenandoahGC "
                                + "(see config/jvm/shenandoah.vmoptions)",
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(),
                config.parallelism(),
                config.databasePath().map(Object::toString).orElse("disabled"),
                config.apiEnabled() ? "enabled on port " + config.apiPort() : "disabled");
    }

    /**
     * The build version, from the jar manifest.
     *
     * <p>Falls back to {@code "dev"} rather than throwing: there is no manifest when running from
     * {@code target/classes} in an IDE, which is most of the time during development, and a
     * {@code --version} that only works in a packaged build is a {@code --version} that is broken exactly
     * when someone is trying to reproduce a report.
     */
    private static String version() {
        String implementation = Main.class.getPackage().getImplementationVersion();
        return implementation == null ? "dev" : implementation;
    }
}
