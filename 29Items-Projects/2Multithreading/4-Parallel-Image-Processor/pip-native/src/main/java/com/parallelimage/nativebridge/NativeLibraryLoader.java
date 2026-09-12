package com.parallelimage.nativebridge;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Finds and loads {@code pip_enhance}, the OpenCV shim, without ever throwing.
 *
 * <h2>Why this class exists at all</h2>
 * {@link System#loadLibrary(String)} is a one-line call, so a loader class looks like ceremony. It is
 * not, for three reasons that only show up on someone else's machine:
 *
 * <ol>
 *   <li><b>{@link UnsatisfiedLinkError} is an {@link Error}, not an exception.</b> Thrown from a
 *       static initializer it becomes {@link ExceptionInInitializerError}, and every later touch of
 *       the class throws {@link NoClassDefFoundError} instead — with the original cause nowhere in
 *       the message. Loading here, eagerly, in a {@code try} that catches {@link Throwable}, turns a
 *       confusing crash into a boolean and one {@code WARNING} line.</li>
 *   <li><b>The library may be inside a jar.</b> {@code loadLibrary} only searches
 *       {@code java.library.path} — real directories. A library shipped as a classpath resource has
 *       to be written to a real file first, which is what {@link #extractAndLoad} does.</li>
 *   <li><b>The failure modes are worth distinguishing.</b> "No {@code pip_enhance} for
 *       windows-x86_64 in this build" is a packaging decision; "found it, but it needs
 *       {@code opencv_world4100.dll} which is not on the PATH" is a user-fixable install problem.
 *       They produce the same {@code UnsatisfiedLinkError}, so {@link #failure()} keeps the message
 *       for the UI's status bar rather than discarding it.</li>
 * </ol>
 *
 * <h2>Load order</h2>
 * <ol>
 *   <li>{@code -Dpip.native.library=/abs/path/pip_enhance.dll} — absolute path, wins outright. This
 *       is how a native developer tests a fresh CMake build without reinstalling anything.</li>
 *   <li>{@code System.loadLibrary("pip_enhance")} — an OS-installed copy on
 *       {@code java.library.path}, {@code PATH}, or {@code LD_LIBRARY_PATH}.</li>
 *   <li>The classpath resource {@code /native/<os>-<arch>/<mapped name>}, extracted to a temp file.
 *       This is the packaged path that works with no user setup.</li>
 * </ol>
 * Deliberately in that order: an explicitly-configured or system-installed library is one the user
 * chose, and it must not be silently shadowed by whatever we happened to bundle.
 *
 * <h2>Thread safety</h2>
 * All state is assigned once during this class's own initialization, which the JVM already
 * serializes, and read-only afterwards. There is no locking because there is nothing left to race.
 */
final class NativeLibraryLoader {

    private static final Logger LOG = System.getLogger(NativeLibraryLoader.class.getName());

    /** Base name, without the {@code lib} prefix or the platform extension. */
    private static final String LIBRARY_NAME = "pip_enhance";

    /** Escape hatch for native developers; see the load order above. */
    private static final String PATH_PROPERTY = "pip.native.library";

    private static final boolean LOADED;
    private static final String FAILURE;
    private static final String SOURCE;

    static {
        Attempt attempt = load();
        LOADED = attempt.loaded();
        FAILURE = attempt.failure();
        SOURCE = attempt.source();
        if (LOADED) {
            LOG.log(Level.INFO, () -> "loaded native enhancer from " + SOURCE);
        } else {
            // WARNING, not ERROR: the application works without this. PassthroughEnhancer takes over
            // and the user gets slightly worse enhancement, not a broken program.
            LOG.log(Level.WARNING, () -> "native enhancer unavailable (" + FAILURE
                    + "); falling back to the pure-Java enhancer");
        }
    }

    private NativeLibraryLoader() {
    }

    /** Whether the shared library is loaded and its native methods may be called. */
    static boolean isLoaded() {
        return LOADED;
    }

    /** Why loading failed, for the status bar; {@code ""} when it succeeded. */
    static String failure() {
        return FAILURE;
    }

    /** Where the loaded library came from, for the startup log; {@code ""} when none loaded. */
    static String source() {
        return SOURCE;
    }

    /**
     * {@code <os>-<arch>}, matching the resource directory layout the CMake build writes into.
     *
     * <p>Normalised rather than passed through: {@code os.arch} reports {@code amd64} on a Windows
     * JDK and {@code x86_64} on a Linux one for the identical CPU, and {@code aarch64} vs
     * {@code arm64} differs by vendor. Without normalisation the resource path would depend on which
     * JDK built the jar.
     */
    static String platformDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String normalisedOs;
        if (os.contains("win")) {
            normalisedOs = "windows";
        } else if (os.contains("mac") || os.contains("darwin")) {
            normalisedOs = "macos";
        } else if (os.contains("nux") || os.contains("nix")) {
            normalisedOs = "linux";
        } else {
            normalisedOs = os.replaceAll("[^a-z0-9]+", "-");
        }

        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String normalisedArch = switch (arch) {
            case "amd64", "x86_64", "x64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> arch.replaceAll("[^a-z0-9]+", "-");
        };
        return normalisedOs + "-" + normalisedArch;
    }

    // ------------------------------------------------------------------------

    private static Attempt load() {
        String configured = System.getProperty(PATH_PROPERTY, "").trim();
        if (!configured.isEmpty()) {
            // A configured path that does not work is a hard error in spirit -- the user asked for
            // this exact file -- but still not worth killing the process over, so it is reported and
            // the remaining strategies are skipped rather than silently papering over the typo.
            try {
                System.load(Path.of(configured).toAbsolutePath().toString());
                return Attempt.ok(PATH_PROPERTY + "=" + configured);
            } catch (Throwable t) {
                return Attempt.failed(describe(PATH_PROPERTY + "=" + configured, t));
            }
        }

        try {
            System.loadLibrary(LIBRARY_NAME);
            return Attempt.ok("java.library.path");
        } catch (Throwable fromSystem) {
            try {
                return Attempt.ok(extractAndLoad());
            } catch (Throwable fromResource) {
                return Attempt.failed(describe("java.library.path", fromSystem)
                        + "; " + describe("bundled resource", fromResource));
            }
        }
    }

    /**
     * Copies the bundled library out of the jar and loads it by absolute path.
     *
     * @return a description of where it came from, for the log
     * @throws IOException if the resource is absent or cannot be written to a temp file
     */
    private static String extractAndLoad() throws IOException {
        String mapped = System.mapLibraryName(LIBRARY_NAME);
        String resource = "native/" + platformDirectory() + "/" + mapped;

        try (InputStream in = NativeLibraryLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("no bundled library at classpath:/" + resource);
            }
            // A per-JVM directory, not a fixed filename: two PIP processes must not race on the same
            // file, and on Windows a loaded DLL cannot be replaced while it is mapped.
            Path directory = Files.createTempDirectory("pip-native-");
            Path target = directory.resolve(mapped);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);

            // Best-effort cleanup. deleteOnExit() cannot remove a mapped DLL on Windows, so the file
            // may survive until the OS cleans %TEMP%; that is a few hundred KB, not a leak worth
            // building a shutdown protocol for.
            target.toFile().deleteOnExit();
            directory.toFile().deleteOnExit();

            System.load(target.toAbsolutePath().toString());
            return "classpath:/" + resource;
        }
    }

    /**
     * Formats a {@link Throwable} as one short clause.
     *
     * <p>No stack trace: the interesting information in an {@link UnsatisfiedLinkError} is entirely in
     * its message (which names the missing dependency), and the trace is our own loader frames.
     */
    private static String describe(String where, Throwable t) {
        String message = t.getMessage();
        return where + ": " + t.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : " " + message.replace('\n', ' '));
    }

    private record Attempt(boolean loaded, String failure, String source) {

        static Attempt ok(String source) {
            return new Attempt(true, "", source);
        }

        static Attempt failed(String failure) {
            return new Attempt(false, failure, "");
        }
    }
}
