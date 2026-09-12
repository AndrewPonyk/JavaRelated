package com.parallelimage.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Reads and writes the small set of window/UI state worth remembering between runs:
 * stage geometry and the last input/output folders.
 *
 * <p>Duplicates {@code AppConfig.expandHome}'s {@code ~}-expansion locally rather than
 * importing it. {@link UiContext}'s javadoc is explicit that pip-ui depends on nothing
 * outside its own module and the core ports; pip-app is neither, so this one small
 * method is copied rather than shared.
 *
 * <p>Backed by a flat {@link Properties} file rather than the SQLite repository: this is
 * per-machine window chrome, not batch history, and does not belong in the data a user
 * might reasonably want to back up or inspect as their processing record.
 */
public final class UiPreferences {

    private static final Logger LOG = System.getLogger(UiPreferences.class.getName());

    private static final String KEY_X = "window.x";
    private static final String KEY_Y = "window.y";
    private static final String KEY_WIDTH = "window.width";
    private static final String KEY_HEIGHT = "window.height";
    private static final String KEY_INPUT_DIR = "lastInputDirectory";
    private static final String KEY_OUTPUT_DIR = "lastOutputDirectory";

    private final Path file;

    public UiPreferences() {
        this(expandHome("~/.pip/ui.properties"));
    }

    /** Visible for tests, which must not touch the real user home directory. */
    UiPreferences(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /** A stage's position and size, saved and restored as a unit so a partial read is never applied. */
    public record Geometry(double x, double y, double width, double height) {
    }

    /** The saved geometry, or empty if none was ever saved or the file is unreadable/incomplete. */
    public Optional<Geometry> loadGeometry() {
        Properties properties = load();
        String x = properties.getProperty(KEY_X);
        String y = properties.getProperty(KEY_Y);
        String width = properties.getProperty(KEY_WIDTH);
        String height = properties.getProperty(KEY_HEIGHT);
        if (x == null || y == null || width == null || height == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Geometry(
                    Double.parseDouble(x), Double.parseDouble(y), Double.parseDouble(width),
                    Double.parseDouble(height)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** The input folder used the last time a batch ran, if one was ever saved. */
    public Optional<Path> lastInputDirectory() {
        return pathProperty(KEY_INPUT_DIR);
    }

    /** The output folder used the last time a batch ran, if one was ever saved. */
    public Optional<Path> lastOutputDirectory() {
        return pathProperty(KEY_OUTPUT_DIR);
    }

    private Optional<Path> pathProperty(String key) {
        String value = load().getProperty(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(expandHome(value));
    }

    /**
     * Writes geometry and directories in one shot, overwriting whatever was saved before.
     *
     * <p>Never throws: losing the window's last position or the last-used folders is cosmetic, and a
     * user should not see a dialog because their preferences file could not be written.
     *
     * @param geometry the stage's current position and size
     * @param inputDirectory the current input folder, or {@code null} if none is set
     * @param outputDirectory the current output folder, or {@code null} if none is set
     */
    public void save(Geometry geometry, Path inputDirectory, Path outputDirectory) {
        Properties properties = new Properties();
        properties.setProperty(KEY_X, String.valueOf(geometry.x()));
        properties.setProperty(KEY_Y, String.valueOf(geometry.y()));
        properties.setProperty(KEY_WIDTH, String.valueOf(geometry.width()));
        properties.setProperty(KEY_HEIGHT, String.valueOf(geometry.height()));
        if (inputDirectory != null) {
            properties.setProperty(KEY_INPUT_DIR, inputDirectory.toString());
        }
        if (outputDirectory != null) {
            properties.setProperty(KEY_OUTPUT_DIR, outputDirectory.toString());
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "Parallel Image Processor UI preferences");
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, () -> "could not save UI preferences to " + file + ": " + e);
        }
    }

    private Properties load() {
        Properties properties = new Properties();
        if (Files.isReadable(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                LOG.log(Level.WARNING, () -> "could not read UI preferences from " + file + ": " + e);
            }
        }
        return properties;
    }

    /** Mirrors {@code AppConfig.expandHome} exactly; see the class javadoc for why it is not shared. */
    private static Path expandHome(String raw) {
        String value = raw.trim();
        if (value.equals("~") || value.startsWith("~/") || value.startsWith("~\\")) {
            String home = System.getProperty("user.home", ".");
            return Path.of(home, value.length() <= 2 ? "" : value.substring(2));
        }
        return Path.of(value);
    }
}
