package com.parallelimage.app.config;

import com.parallelimage.core.fork.ForkJoinConfig;
import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.pipeline.ImageOperation;
import com.parallelimage.core.pipeline.PipelineFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * The application's entire configuration, resolved once at startup.
 *
 * <h2>The one place {@code System.getenv} is called</h2>
 * Not a style rule — a testability one. A class that reads an environment variable can only be tested
 * by a process launched with that variable set, which in practice means it is not tested. Every other
 * class in this project takes its settings as constructor arguments and can therefore be exercised with
 * whatever values a test wants. {@code AppConfig} is the single point where the outside world is read,
 * and it is a plain value object afterwards.
 *
 * <p>It also makes the configuration surface enumerable. {@link #describe()} prints every setting and
 * where it came from, which turns "why is it using four threads?" from an investigation into one line of
 * output.
 *
 * <h2>Precedence, highest first</h2>
 * <ol>
 *   <li><b>System properties</b> ({@code -Dpip.parallelism=8}). Highest because they are the most
 *       explicit and the most local: a developer overriding one thing for one run should not have to
 *       edit a file that is under version control.</li>
 *   <li><b>Environment variables</b> ({@code PIP_PARALLELISM}). How a service manager or a CI job
 *       supplies configuration, and the only mechanism available to a launcher script that must not
 *       rewrite files.</li>
 *   <li><b>{@code config/application.properties}</b> next to the working directory, then the same
 *       resource on the classpath. The operator's file wins over the packaged default; the packaged
 *       default guarantees the application starts with no configuration at all.</li>
 *   <li><b>Compiled-in defaults</b> from {@link ProcessingOptions} and {@link ForkJoinConfig}.</li>
 * </ol>
 *
 * <h2>Naming</h2>
 * A property {@code batches.parallelism} maps to the environment variable {@code PIP_PARALLELISM} — not
 * to {@code PIP_BATCHES_PARALLELISM}. The mapping is explicit in {@link #ENV_ALIASES} rather than
 * mechanical, because a mechanical transform produces names nobody would guess and a typo in one then
 * silently does nothing.
 */
public final class AppConfig {

    private static final Logger LOG = System.getLogger(AppConfig.class.getName());

    /** Where the operator's file lives, relative to the working directory. */
    public static final Path EXTERNAL_FILE = Path.of("config", "application.properties");

    /** Packaged fallback, so a bare {@code java -jar} still has sensible values. */
    private static final String CLASSPATH_RESOURCE = "/config/application.properties";

    private static final String PREFIX = "pip.";

    /**
     * Property name to environment variable. Explicit, not derived — see the class javadoc.
     *
     * <p>Insertion-ordered so {@link #describe()} lists settings in a stable, readable order rather
     * than in {@link String#hashCode()} order, which changes the diff every time a key is added.
     */
    private static final Map<String, String> ENV_ALIASES = new LinkedHashMap<>();

    static {
        ENV_ALIASES.put("db.path", "PIP_DB_PATH");
        ENV_ALIASES.put("db.enabled", "PIP_DB_ENABLED");
        ENV_ALIASES.put("batches.parallelism", "PIP_PARALLELISM");
        ENV_ALIASES.put("batches.tileThresholdPixels", "PIP_TILE_THRESHOLD");
        ENV_ALIASES.put("batches.batchThresholdJobs", "PIP_BATCH_THRESHOLD");
        ENV_ALIASES.put("batches.maxPixelsPerImage", "PIP_MAX_PIXELS");
        ENV_ALIASES.put("output.format", "PIP_OUTPUT_FORMAT");
        ENV_ALIASES.put("output.quality", "PIP_OUTPUT_QUALITY");
        ENV_ALIASES.put("output.stripMetadata", "PIP_STRIP_METADATA");
        ENV_ALIASES.put("output.overwriteExisting", "PIP_OVERWRITE");
        ENV_ALIASES.put("pipeline.default", "PIP_PIPELINE");
        ENV_ALIASES.put("api.enabled", "PIP_API_ENABLED");
        ENV_ALIASES.put("api.port", "PIP_API_PORT");
        ENV_ALIASES.put("api.token", "PIP_API_TOKEN");
        ENV_ALIASES.put("retention.days", "PIP_RETENTION_DAYS");
        ENV_ALIASES.put("log.format", "PIP_LOG_FORMAT");
        ENV_ALIASES.put("ui.locale", "PIP_LOCALE");
    }

    private final Properties properties;
    private final Map<String, String> sources;

    private AppConfig(Properties properties, Map<String, String> sources) {
        this.properties = properties;
        this.sources = Map.copyOf(sources);
    }

    /** Resolves configuration from all four layers. Never throws for a missing file. */
    public static AppConfig load() {
        return load(EXTERNAL_FILE);
    }

    /**
     * Resolves configuration, reading the operator's file from {@code externalFile}.
     *
     * <p>The parameter exists for tests: it is the only input that is not process-global, so a test can
     * point it at a temporary file and get a fully-populated config without touching the environment.
     */
    public static AppConfig load(Path externalFile) {
        Properties resolved = new Properties();
        Map<String, String> sources = new LinkedHashMap<>();

        loadClasspathDefaults(resolved, sources);
        loadExternal(externalFile, resolved, sources);
        applyEnvironment(resolved, sources);
        applySystemProperties(resolved, sources);

        return new AppConfig(resolved, sources);
    }

    private static void loadClasspathDefaults(Properties into, Map<String, String> sources) {
        try (InputStream in = AppConfig.class.getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (in == null) {
                // Not a warning: a jar built without the resource is unusual but entirely workable,
                // because every setting has a compiled-in default.
                LOG.log(Level.DEBUG, () -> "no packaged " + CLASSPATH_RESOURCE);
                return;
            }
            Properties packaged = new Properties();
            packaged.load(in);
            merge(packaged, into, sources, "packaged defaults");
        } catch (IOException e) {
            // A resource on our own classpath that cannot be read is a packaging fault, not a user
            // error, so it is logged loudly and then ignored rather than made fatal.
            LOG.log(Level.WARNING, () -> "could not read " + CLASSPATH_RESOURCE + ": " + e);
        }
    }

    private static void loadExternal(Path file, Properties into, Map<String, String> sources) {
        if (file == null || !Files.isReadable(file)) {
            return;
        }
        // Properties.load(InputStream) is ISO-8859-1; load(Reader) is not. A watermark default with
        // an accented character would otherwise arrive mojibaked, and the symptom -- wrong glyphs
        // burned into 4 000 output images -- is expensive to discover late. The reader, not the raw
        // stream, is the try-with-resources variable so there is exactly one resource to account for.
        try (Reader in = new java.io.InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            Properties external = new Properties();
            external.load(in);
            merge(external, into, sources, file.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    private static void applyEnvironment(Properties into, Map<String, String> sources) {
        ENV_ALIASES.forEach((key, envName) -> {
            String value = System.getenv(envName);
            if (value != null && !value.isBlank()) {
                into.setProperty(key, value.trim());
                sources.put(key, "$" + envName);
            }
        });
    }

    private static void applySystemProperties(Properties into, Map<String, String> sources) {
        // Iterate the known keys rather than scanning every system property: the JVM has hundreds, and
        // a stray -Dpip.something typo should be visible by its absence from describe() rather than
        // silently accepted into the map.
        ENV_ALIASES.keySet().forEach(key -> {
            String value = System.getProperty(PREFIX + key);
            if (value != null && !value.isBlank()) {
                into.setProperty(key, value.trim());
                sources.put(key, "-D" + PREFIX + key);
            }
        });
    }

    private static void merge(Properties from, Properties into, Map<String, String> sources, String origin) {
        from.stringPropertyNames().forEach(key -> {
            into.setProperty(key, from.getProperty(key).trim());
            sources.put(key, origin);
        });
    }

    // ------------------------------------------------------------------------
    //  Typed access
    // ------------------------------------------------------------------------

    /**
     * Where the SQLite file lives.
     *
     * <p>{@link Optional#empty()} means history is switched off — {@code db.enabled=false} or
     * {@code PIP_DB_ENABLED=false}. That is a supported configuration, not a degraded one: it is how the
     * CLI's {@code --no-history} works and how the application runs from a read-only network share.
     */
    public Optional<Path> databasePath() {
        if (!bool("db.enabled", true)) {
            return Optional.empty();
        }
        String configured = properties.getProperty("db.path");
        if (configured == null || configured.isBlank()) {
            return Optional.of(com.parallelimage.persistence.jdbc.Database.defaultPath());
        }
        return Optional.of(expandHome(configured));
    }

    /**
     * Worker count for the engine's pool.
     *
     * <p>Zero or a negative configured value falls back to {@link ForkJoinConfig#defaultParallelism()}
     * rather than being rejected: {@code PIP_PARALLELISM=0} from a script that failed to compute a value
     * should start the application, not refuse to.
     */
    public int parallelism() {
        int configured = integer("batches.parallelism", 0);
        return configured > 0 ? configured : ForkJoinConfig.defaultParallelism();
    }

    /**
     * The options a fresh batch starts from, with the operator's tuning applied.
     *
     * <p>Every numeric setting here goes through a range-checking reader rather than the plain
     * {@link #number}/{@link #longValue}/{@link #integer} ones. {@link ProcessingOptions}'s constructor
     * validates its arguments — deliberately, so a bad configuration cannot reach a fork/join worker —
     * and an unchecked value from a properties file would therefore reach it as a thrown
     * {@link IllegalArgumentException} during startup. One character out of place in
     * {@code output.quality} would stop the application from starting at all, which is precisely what
     * {@link #defaultPipeline()} already argues against: the operator's tool for fixing configuration is
     * the UI, and the UI needs the application to be running.
     */
    public ProcessingOptions defaultOptions() {
        ProcessingOptions compiled = ProcessingOptions.defaults();
        return ProcessingOptions.builder()
                .operations(defaultPipeline())
                .outputFormat(string("output.format", compiled.outputFormat()))
                .quality((float) boundedNumber("output.quality", compiled.quality(), 0.0d, 1.0d))
                .tileThresholdPixels(positiveLong("batches.tileThresholdPixels", compiled.tileThresholdPixels()))
                .batchThresholdJobs(positiveInteger("batches.batchThresholdJobs", compiled.batchThresholdJobs()))
                .stripMetadata(bool("output.stripMetadata", compiled.stripMetadata()))
                .overwriteExisting(bool("output.overwriteExisting", compiled.overwriteExisting()))
                .maxPixelsPerImage(positiveLong("batches.maxPixelsPerImage", compiled.maxPixelsPerImage()))
                .build();
    }

    /**
     * The configured default pipeline.
     *
     * <p>An unparseable value is logged and treated as empty rather than made fatal. The alternative —
     * refusing to start — means one typo in a properties file leaves the operator with no UI in which to
     * fix it.
     */
    private List<ImageOperation> defaultPipeline() {
        String text = properties.getProperty("pipeline.default", "");
        try {
            return PipelineFormat.parse(text);
        } catch (IllegalArgumentException e) {
            LOG.log(Level.WARNING, () -> "ignoring unparseable pipeline.default: " + e.getMessage());
            return List.of();
        }
    }

    /** Whether the local control API should be started. Off unless explicitly enabled. */
    public boolean apiEnabled() {
        return bool("api.enabled", false);
    }

    /**
     * Port for the local control API.
     *
     * <p>Deliberately <em>not</em> range-checked the way {@link #defaultOptions()}'s numbers are. An
     * out-of-range port fails loudly when the server binds, and that is the better outcome: falling back
     * to 8137 would leave a client that was told to use port 99999 unable to connect to something that is
     * nevertheless running, which is harder to diagnose than a refusal. The settings that fall back all
     * have defaults that still do what the operator asked, only tuned differently.
     */
    public int apiPort() {
        return integer("api.port", 8137);
    }

    /**
     * Shared secret the control API requires, if any.
     *
     * <p>Empty is the documented default and is safe <em>only</em> because the server binds to the
     * loopback interface: see {@code BatchJobController}. Any change that binds a wider address must
     * make this mandatory.
     */
    public Optional<String> apiToken() {
        String token = properties.getProperty("api.token", "");
        return token.isBlank() ? Optional.empty() : Optional.of(token);
    }

    /**
     * How many days of finished-batch history to keep, or 0 to keep it forever.
     *
     * <p>Unlike {@link #parallelism()}, zero here is not "unset, use the default" — it is the default,
     * and it means retention is switched off. There is no fallback value for "how long to keep history"
     * the way there is a computed one for worker count, so a negative or unparseable configured value is
     * simply floored at zero rather than warned about and replaced.
     */
    public int retentionDays() {
        return Math.max(0, integer("retention.days", 0));
    }

    /**
     * Log output format: {@code "text"} (the JDK's default console formatting, unchanged) or
     * {@code "json"} (one {@link com.parallelimage.app.logging.JsonLogHandler} record per line).
     * Any other configured value is treated as {@code "text"} rather than made fatal, consistent
     * with {@link #defaultPipeline()}'s warn-and-continue approach to bad configuration.
     */
    public String logFormat() {
        return string("log.format", "text");
    }

    /**
     * The UI/CLI display language.
     *
     * <p>An unrecognised or unconfigured tag falls back to {@link Locale#ROOT} (the bundles' own
     * default, English) rather than to {@link Locale#getDefault()} — the JVM's platform default is
     * whatever locale the host OS happens to report, and a batch tool's output changing language
     * because it moved to a different machine is a worse surprise than it always being English until
     * asked otherwise.
     */
    public Locale locale() {
        String tag = string("ui.locale", "");
        return tag.isBlank() ? Locale.ROOT : Locale.forLanguageTag(tag);
    }

    // ------------------------------------------------------------------------
    //  Primitives
    // ------------------------------------------------------------------------

    public String string(String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Parses an int, falling back on anything unparseable.
     *
     * <p>Warn-and-continue rather than throw, consistently for every numeric setting. A configuration
     * file is edited by hand; {@code parallelism=8 } with a stray comment on the end should not prevent
     * an application from starting, but it must not be silent either or the operator will believe the
     * value took effect.
     */
    public int integer(String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOG.log(Level.WARNING, () -> key + "='" + value + "' is not an integer; using " + fallback);
            return fallback;
        }
    }

    public long longValue(String key, long fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            LOG.log(Level.WARNING, () -> key + "='" + value + "' is not a number; using " + fallback);
            return fallback;
        }
    }

    public double number(String key, double fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            LOG.log(Level.WARNING, () -> key + "='" + value + "' is not a number; using " + fallback);
            return fallback;
        }
    }

    /**
     * A number that must fall within a range, warning and falling back when it does not.
     *
     * <p>Written as a negated in-range test rather than "below min or above max". The latter is
     * {@code false} for {@code NaN} — and {@link Double#parseDouble} accepts the text {@code "NaN"} — so
     * the one value guaranteed to break every downstream comparison would be the one value that passed.
     */
    private double boundedNumber(String key, double fallback, double min, double max) {
        double parsed = number(key, fallback);
        if (!(parsed >= min && parsed <= max)) {
            LOG.log(Level.WARNING, () -> key + "='" + parsed + "' is outside " + min + ".." + max
                    + "; using " + fallback);
            return fallback;
        }
        return parsed;
    }

    /**
     * A count or size that must be positive, warning and falling back when it is not.
     *
     * <p>Zero is refused rather than treated as "unset", unlike {@link #parallelism()}, because zero is a
     * meaningful-looking value for all three settings that use this and a catastrophic one for each: a
     * tile threshold of zero splits until every leaf is one pixel, and a pixel budget of zero rejects
     * every image as a decode bomb.
     */
    private long positiveLong(String key, long fallback) {
        long parsed = longValue(key, fallback);
        if (parsed <= 0) {
            LOG.log(Level.WARNING, () -> key + "='" + parsed + "' must be positive; using " + fallback);
            return fallback;
        }
        return parsed;
    }

    /** {@link #positiveLong} for an {@code int} setting. */
    private int positiveInteger(String key, int fallback) {
        int parsed = integer(key, fallback);
        if (parsed <= 0) {
            LOG.log(Level.WARNING, () -> key + "='" + parsed + "' must be positive; using " + fallback);
            return fallback;
        }
        return parsed;
    }

    /**
     * Parses a boolean strictly.
     *
     * <p>Not {@link Boolean#parseBoolean}, which maps every unrecognised string — including
     * {@code "yes"}, {@code "1"} and {@code "ture"} — to {@code false}. For a setting like
     * {@code db.enabled} that silent {@code false} disables history and looks like a bug in the
     * repository. Accepted spellings are listed; anything else warns and keeps the default.
     */
    public boolean bool(String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> {
                LOG.log(Level.WARNING, () -> key + "='" + value + "' is not a boolean; using " + fallback);
                yield fallback;
            }
        };
    }

    /**
     * Expands a leading {@code ~} to the user's home directory.
     *
     * <p>The shell does this for a value typed on a command line but not for one read from a properties
     * file, and {@code Path.of("~/.pip/pip.db")} creates a directory literally named {@code ~} in the
     * working directory — which is confusing to find and easy to leave behind.
     */
    private static Path expandHome(String raw) {
        String value = raw.trim();
        if (value.equals("~") || value.startsWith("~/") || value.startsWith("~\\")) {
            String home = System.getProperty("user.home", ".");
            return Path.of(home, value.length() <= 2 ? "" : value.substring(2));
        }
        return Path.of(value);
    }

    // ------------------------------------------------------------------------
    //  Diagnostics
    // ------------------------------------------------------------------------

    /**
     * Every setting, its value, and where it came from.
     *
     * <p>{@code api.token} is redacted. It is the only secret this application has, and a support
     * transcript containing a startup dump is exactly how such a value escapes.
     */
    public String describe() {
        StringBuilder out = new StringBuilder("configuration:");
        ENV_ALIASES.keySet().forEach(key -> {
            String value = properties.getProperty(key);
            if (value == null) {
                return;
            }
            String shown = key.equals("api.token") ? "***" : value;
            out.append("\n  ").append(key).append(" = ").append(shown)
                    .append("   (").append(sources.getOrDefault(key, "default")).append(')');
        });
        return out.toString();
    }

    @Override
    public String toString() {
        return "AppConfig[" + properties.size() + " settings]";
    }
}
