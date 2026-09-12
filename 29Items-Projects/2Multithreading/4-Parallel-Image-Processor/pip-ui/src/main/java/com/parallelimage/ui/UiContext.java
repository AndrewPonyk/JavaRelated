package com.parallelimage.ui;

import com.parallelimage.core.engine.ImageProcessingEngine;
import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.port.JobRepository;
import java.util.Locale;
import java.util.Objects;

/**
 * Everything the UI needs from the rest of the application, handed in from outside.
 *
 * <h2>Why a context object and not a service locator</h2>
 * JavaFX instantiates the {@link javafx.application.Application} subclass itself, reflectively,
 * through its no-argument constructor. There is no seam to inject through — which is why so many
 * JavaFX applications end up with a static {@code ServiceLocator} that every view reaches into, and
 * then nothing in the UI can be tested without standing up the whole application.
 *
 * <p>This is the compromise that keeps the seam: exactly one static field exists
 * ({@link PipApplication#prepare}), it is written once before {@code launch()} and read once inside
 * {@code start()}, and from that point on the context is passed down the view tree as a normal
 * constructor argument. Every view and view model therefore takes its collaborators explicitly and a
 * test can hand it {@link JobRepository#NO_OP} and a stub engine.
 *
 * <h2>Ports, not implementations</h2>
 * The type here is the {@link JobRepository} <em>port</em>, not {@code SqliteJobRepository}.
 * {@code pip-ui} has no dependency on {@code pip-persistence} and must not acquire one: the whole
 * point of the hexagonal layout is that the view can be driven by an in-memory fake. If you find
 * yourself wanting to import a persistence class here, the missing piece belongs on the port.
 *
 * @param engine the processing façade; the UI owns neither the pool nor its lifecycle
 * @param repository history, for the results table; {@link JobRepository#NO_OP} is a valid choice and
 *     produces an application whose history tab is permanently empty rather than one that crashes
 * @param defaultOptions options a fresh batch starts from, so the operator's configured defaults
 *     (config/application.properties) show up in the UI rather than being silently overridden by
 *     {@link ProcessingOptions#defaults()}
 * @param enhancerDescription what {@code ImageEnhancer.discover().describe()} returned at startup,
 *     for the status bar. Resolved once in {@code pip-app} because SPI discovery performs classpath
 *     scanning and must not happen on the FX thread.
 * @param locale the language the UI renders in, from {@code AppConfig.locale()}; defaults to
 *     {@link Locale#ROOT} rather than the JVM default so a headless test run is deterministic.
 */
public record UiContext(
        ImageProcessingEngine engine,
        JobRepository repository,
        ProcessingOptions defaultOptions,
        String enhancerDescription,
        Locale locale) {

    public UiContext {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(defaultOptions, "defaultOptions");
        enhancerDescription = enhancerDescription == null ? "unknown" : enhancerDescription;
        locale = locale == null ? Locale.ROOT : locale;
    }
}
