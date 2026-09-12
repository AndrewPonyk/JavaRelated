package com.parallelimage.core.spi;

import com.parallelimage.core.pipeline.ImageOperation.EnhanceMode;
import java.awt.image.BufferedImage;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Service-provider interface for ML-based image enhancement.
 *
 * <h2>Why an SPI instead of a direct call into {@code pip-native}</h2>
 * The native path is the one part of this application that can take the whole JVM down: a segfault
 * inside OpenCV is not an exception, it is a process death, and a missing {@code opencv_java4xx.dll}
 * is an {@link UnsatisfiedLinkError} at class-initialization time rather than a catchable failure at
 * the call site. Hiding it behind a {@link ServiceLoader} boundary means:
 * <ul>
 *   <li>{@code pip-core} compiles and its tests run with no native toolchain present at all;</li>
 *   <li>a user with no OpenCV installed gets {@link PassthroughEnhancer} and a {@code WARNING}
 *       instead of a startup crash;</li>
 *   <li>the native module can be dropped from a {@code jlink} image to produce a pure-Java build
 *       with no code change.</li>
 * </ul>
 *
 * <h2>Implementation contract</h2>
 * Thread-safe and re-entrant: several workers will call {@link #enhance} concurrently. Native
 * implementations must therefore either be stateless or guard per-call native handles — and must
 * declare their blocking behaviour to the pool via {@code ForkJoinPool.ManagedBlocker} if a call can
 * park for a long time (TECH-NOTES §3.6 F5).
 */
public interface ImageEnhancer {

    /**
     * Enhances {@code source} and returns the result.
     *
     * <p>May return {@code source} itself when the mode is unsupported — callers must not assume a
     * fresh instance.
     *
     * @param strength 0.0..1.0, algorithm-specific intensity
     */
    BufferedImage enhance(BufferedImage source, EnhanceMode mode, double strength);

    /**
     * Whether this provider can actually run right now.
     *
     * <p>Separate from mere presence on the classpath: a native provider is present but unavailable
     * when its shared library is missing. Checked once at startup, never per image.
     */
    boolean isAvailable();

    /** Human-readable identity for the log line and the UI status bar. */
    String describe();

    /**
     * Selection order; highest wins. {@link PassthroughEnhancer} uses {@link Integer#MIN_VALUE} so
     * any real provider outranks it.
     */
    default int priority() {
        return 0;
    }

    /** Modes this provider implements natively; others fall through to a copy. */
    default boolean supports(EnhanceMode mode) {
        return true;
    }

    /**
     * Discovers the highest-priority available provider, falling back to
     * {@link PassthroughEnhancer}.
     *
     * <p>Deliberately never throws and never returns {@code null}: enhancement is a nice-to-have, and
     * "no OpenCV on this machine" must degrade the output, not fail the batch. Call once at startup
     * and cache the result — {@code ServiceLoader} iteration performs classpath scanning and
     * class-initialization, which is far too expensive per image.
     */
    static ImageEnhancer discover() {
        Logger log = System.getLogger(ImageEnhancer.class.getName());
        List<ImageEnhancer> candidates = new ArrayList<>();
        try {
            for (ImageEnhancer candidate : ServiceLoader.load(ImageEnhancer.class)) {
                try {
                    if (candidate.isAvailable()) {
                        candidates.add(candidate);
                    } else {
                        log.log(Level.DEBUG,
                                () -> "enhancer present but unavailable: " + candidate.describe());
                    }
                } catch (RuntimeException | LinkageError e) {
                    // A provider whose static initializer blew up must not prevent the others from
                    // being considered. LinkageError is the expected shape of "no native library".
                    log.log(Level.WARNING, "enhancer rejected during availability check", e);
                }
            }
        } catch (java.util.ServiceConfigurationError e) {
            // Thrown by the iterator itself for a malformed META-INF/services descriptor or a
            // provider that cannot be instantiated. Not fatal: the fallback below still applies.
            log.log(Level.WARNING, "malformed ImageEnhancer service descriptor; ignoring", e);
        }

        ImageEnhancer chosen = candidates.stream()
                .max(Comparator.comparingInt(ImageEnhancer::priority))
                .orElseGet(PassthroughEnhancer::new);
        log.log(Level.INFO, () -> "image enhancer: " + chosen.describe());
        return chosen;
    }
}
