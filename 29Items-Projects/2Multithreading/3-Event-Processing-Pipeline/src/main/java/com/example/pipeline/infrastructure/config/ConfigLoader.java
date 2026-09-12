package com.example.pipeline.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves {@link PipelineConfig} from the layered configuration sources.
 *
 * <p>Precedence, lowest first:
 * <ol>
 *   <li>the defaults compiled into {@link PipelineConfig.Builder};</li>
 *   <li>{@code application.properties} on the classpath (the committed baseline);</li>
 *   <li>{@code config/application-<env>.properties} on disk (per-environment);</li>
 *   <li>environment variables — {@code PIPELINE_QUEUE_CAPACITY} style;</li>
 *   <li>CLI flags — {@code --pipeline.queue.capacity=128}, which always win.</li>
 * </ol>
 *
 * <p><strong>Why environment variables sit above files:</strong> a container or CI job
 * can override a value without rebuilding the artifact, and secrets stay out of the
 * repository. CLI flags sit above everything so an operator can always win over an
 * inherited environment, which is what you want at 3 a.m.
 *
 * <p>The profile itself is resolved first (from {@code PIPELINE_ENV} or
 * {@code --pipeline.env=...}) because it decides which file layer 3 reads.
 *
 * <p>Unknown keys are rejected rather than ignored: a silently-ignored
 * {@code --pipeline.consumer.thread=8} typo is a config bug that only shows up as odd
 * throughput hours later. The same check runs over both property-file layers, and the
 * message names the file so a typo in {@code config/application-prod.properties} is as
 * actionable as one on the command line. Environment variables need no such check because
 * {@link #readEnvironment()} only ever looks up the names it knows — the process
 * environment is shared with every other tool on the box, so enumerating and rejecting
 * {@code PIPELINE_*} names would be claiming a namespace this application does not own.
 */
public final class ConfigLoader {

    /** Classpath resource holding the committed baseline. */
    public static final String CLASSPATH_RESOURCE = "application.properties";

    /** Directory holding the per-environment property files. */
    public static final String CONFIG_DIR = "config";

    /** Prefix every environment variable must carry. */
    public static final String ENV_PREFIX = "PIPELINE_";

    private static final Logger LOG = Logger.getLogger(ConfigLoader.class.getName());

    /** Every recognised property key, in declaration order — also the {@code --help} source. */
    private static final List<String> KNOWN_KEYS = List.of(
        PipelineConfig.KEY_ENV,
        PipelineConfig.KEY_EVENT_COUNT,
        PipelineConfig.KEY_EVENTS_PER_SECOND,
        PipelineConfig.KEY_DURATION_SECONDS,
        PipelineConfig.KEY_BATCH_SIZE,
        PipelineConfig.KEY_SENSOR_COUNT,
        PipelineConfig.KEY_RANDOM_SEED,
        PipelineConfig.KEY_QUEUE_CAPACITY,
        PipelineConfig.KEY_CONSUMER_THREADS,
        PipelineConfig.KEY_FILTER_THRESHOLD,
        PipelineConfig.KEY_AGGREGATION_PARALLELISM,
        PipelineConfig.KEY_SEQUENTIAL_THRESHOLD,
        PipelineConfig.KEY_SHUTDOWN_TIMEOUT_SECONDS,
        PipelineConfig.KEY_POLL_TIMEOUT_MILLIS,
        PipelineConfig.KEY_DASHBOARD_ENABLED,
        PipelineConfig.KEY_DASHBOARD_INTERVAL_MILLIS,
        PipelineConfig.KEY_LOG_LEVEL,
        PipelineConfig.KEY_HTTP_ENABLED,
        PipelineConfig.KEY_HTTP_PORT,
        PipelineConfig.KEY_HTTP_TOKEN,
        PipelineConfig.KEY_JDBC_URL,
        PipelineConfig.KEY_JDBC_USER,
        PipelineConfig.KEY_JDBC_PASSWORD,
        PipelineConfig.KEY_OUTPUT_DIR);

    private final UnaryOperator<String> environment;
    private final Path configDir;

    /** Loader reading the real process environment and {@code ./config}. */
    public ConfigLoader() {
        this(System::getenv, Path.of(CONFIG_DIR));
    }

    /**
     * @param environment environment-variable lookup; injected so tests need no real env vars
     * @param configDir   directory holding {@code application-<env>.properties}
     */
    public ConfigLoader(UnaryOperator<String> environment, Path configDir) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.configDir = Objects.requireNonNull(configDir, "configDir");
    }

    /**
     * Every recognised key, in declaration order.
     *
     * <p>Returned as an unmodifiable {@code List} rather than a defensive array copy so
     * callers can do the two things they actually want — {@code contains} for the
     * unknown-key check and iteration for {@code --help} — without either cloning or the
     * risk of mutating shared state.
     *
     * @return the known property keys
     */
    public static List<String> knownKeys() {
        return KNOWN_KEYS;
    }

    /**
     * Environment-variable name for a property key: upper-snake-cased, prefixed.
     *
     * <p>{@code pipeline.queue.capacity -> PIPELINE_QUEUE_CAPACITY}. Mechanical, so a
     * new setting needs no lookup table and no documentation drift.
     */
    public static String envVarFor(String key) {
        String withoutPrefix = key.startsWith("pipeline.") ? key.substring("pipeline.".length()) : key;
        return ENV_PREFIX + withoutPrefix.replace('.', '_').toUpperCase(Locale.ROOT);
    }

    /**
     * Resolves the configuration.
     *
     * @param cliOverrides already-parsed CLI flags, keyed by full property key
     * @return the validated configuration
     * @throws ConfigurationException if a value is unparseable, out of range, or unknown
     */
    public PipelineConfig load(Map<String, String> cliOverrides) {
        Objects.requireNonNull(cliOverrides, "cliOverrides");
        rejectUnknownKeys(cliOverrides, "the command line");

        Map<String, String> values = new LinkedHashMap<>();
        values.putAll(readClasspath());
        String env = firstNonBlank(cliOverrides.get(PipelineConfig.KEY_ENV),
                environment.apply(envVarFor(PipelineConfig.KEY_ENV)),
                values.get(PipelineConfig.KEY_ENV),
                "dev");
        values.putAll(readProfile(env));
        values.putAll(readEnvironment());
        values.putAll(cliOverrides);
        values.put(PipelineConfig.KEY_ENV, env);

        PipelineConfig.Builder builder = PipelineConfig.builder();
        // Applied through the same typed parsers regardless of which layer won, so a
        // malformed value reports the same message whether it came from a file or a flag.
        applyString(values, PipelineConfig.KEY_ENV, builder::env);
        applyLong(values, PipelineConfig.KEY_EVENT_COUNT, builder::eventCount);
        applyLong(values, PipelineConfig.KEY_EVENTS_PER_SECOND, builder::eventsPerSecond);
        applyLong(values, PipelineConfig.KEY_DURATION_SECONDS, builder::durationSeconds);
        applyInt(values, PipelineConfig.KEY_BATCH_SIZE, builder::batchSize);
        applyInt(values, PipelineConfig.KEY_SENSOR_COUNT, builder::sensorCount);
        applyLong(values, PipelineConfig.KEY_RANDOM_SEED, builder::randomSeed);
        applyInt(values, PipelineConfig.KEY_QUEUE_CAPACITY, builder::queueCapacity);
        applyInt(values, PipelineConfig.KEY_CONSUMER_THREADS, builder::consumerThreads);
        applyDouble(values, PipelineConfig.KEY_FILTER_THRESHOLD, builder::filterThreshold);
        applyInt(values, PipelineConfig.KEY_AGGREGATION_PARALLELISM, builder::aggregationParallelism);
        applyInt(values, PipelineConfig.KEY_SEQUENTIAL_THRESHOLD, builder::sequentialThreshold);
        applyLong(values, PipelineConfig.KEY_SHUTDOWN_TIMEOUT_SECONDS, builder::shutdownTimeoutSeconds);
        applyLong(values, PipelineConfig.KEY_POLL_TIMEOUT_MILLIS, builder::pollTimeoutMillis);
        applyBoolean(values, PipelineConfig.KEY_DASHBOARD_ENABLED, builder::dashboardEnabled);
        applyLong(values, PipelineConfig.KEY_DASHBOARD_INTERVAL_MILLIS, builder::dashboardIntervalMillis);
        applyString(values, PipelineConfig.KEY_LOG_LEVEL, builder::logLevel);
        applyBoolean(values, PipelineConfig.KEY_HTTP_ENABLED, builder::httpEnabled);
        applyInt(values, PipelineConfig.KEY_HTTP_PORT, builder::httpPort);
        applyString(values, PipelineConfig.KEY_HTTP_TOKEN, builder::httpToken);
        applyString(values, PipelineConfig.KEY_JDBC_URL, builder::jdbcUrl);
        applyString(values, PipelineConfig.KEY_JDBC_USER, builder::jdbcUser);
        applyString(values, PipelineConfig.KEY_JDBC_PASSWORD, builder::jdbcPassword);
        applyString(values, PipelineConfig.KEY_OUTPUT_DIR, builder::outputDir);
        return builder.build();
    }

    /**
     * Fails on any key this application does not recognise.
     *
     * @param values the layer's parsed key/value pairs
     * @param source where they came from, for the message — a file path or "the command line"
     */
    private static void rejectUnknownKeys(Map<String, String> values, String source) {
        for (String key : values.keySet()) {
            if (!KNOWN_KEYS.contains(key)) {
                throw new ConfigurationException(key,
                        "is not a recognised setting (in " + source + "); run with --help");
            }
        }
    }

    private Map<String, String> readClasspath() {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (in == null) {
                LOG.log(Level.FINE, "no {0} on the classpath; using compiled defaults", CLASSPATH_RESOURCE);
                return Map.of();
            }
            Properties properties = new Properties();
            properties.load(in);
            Map<String, String> values = toMap(properties);
            rejectUnknownKeys(values, CLASSPATH_RESOURCE);
            return values;
        } catch (IOException e) {
            throw new ConfigurationException(CLASSPATH_RESOURCE, "could not be read from the classpath", e);
        }
    }

    private Map<String, String> readProfile(String env) {
        Path file = configDir.resolve("application-" + env + ".properties");
        if (!Files.isRegularFile(file)) {
            // Absent profile files are normal: the classpath baseline plus env vars is a
            // complete configuration on its own.
            LOG.log(Level.FINE, "no profile file at {0}", file);
            return Map.of();
        }
        try (InputStream in = Files.newInputStream(file)) {
            Properties properties = new Properties();
            properties.load(in);
            Map<String, String> values = toMap(properties);
            rejectUnknownKeys(values, file.toString());
            LOG.log(Level.FINE, "loaded profile {0}", file);
            return values;
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }

    private Map<String, String> readEnvironment() {
        Map<String, String> found = new LinkedHashMap<>();
        for (String key : KNOWN_KEYS) {
            String value = environment.apply(envVarFor(key));
            if (value != null && !value.isBlank()) {
                found.put(key, value.strip());
            }
        }
        return found;
    }

    private static Map<String, String> toMap(Properties properties) {
        Map<String, String> map = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            String value = properties.getProperty(name);
            if (value != null && !value.isBlank()) {
                map.put(name, value.strip());
            }
        }
        return map;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.strip();
            }
        }
        return "";
    }

    /** Reads {@code key} as text and hands it to {@code setter} when present. */
    private static void applyString(Map<String, String> values, String key, StringSetter setter) {
        String value = values.get(key);
        if (value != null) {
            setter.set(value);
        }
    }

    /** Reads {@code key} as an {@code int} and hands it to {@code setter} when present. */
    private static void applyInt(Map<String, String> values, String key, IntSetter setter) {
        String value = values.get(key);
        if (value == null) {
            return;
        }
        try {
            setter.set(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            throw new ConfigurationException(key, "must be an integer but was '" + value + "'", e);
        }
    }

    /** Reads {@code key} as a {@code long} and hands it to {@code setter} when present. */
    private static void applyLong(Map<String, String> values, String key, LongSetter setter) {
        String value = values.get(key);
        if (value == null) {
            return;
        }
        try {
            setter.set(Long.parseLong(value));
        } catch (NumberFormatException e) {
            throw new ConfigurationException(key, "must be a whole number but was '" + value + "'", e);
        }
    }

    /** Reads {@code key} as a {@code double} and hands it to {@code setter} when present. */
    private static void applyDouble(Map<String, String> values, String key, DoubleSetter setter) {
        String value = values.get(key);
        if (value == null) {
            return;
        }
        try {
            setter.set(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            throw new ConfigurationException(key, "must be a decimal number but was '" + value + "'", e);
        }
    }

    /**
     * Reads {@code key} as a boolean, rejecting anything but {@code true}/{@code false}.
     *
     * <p>{@code Boolean.parseBoolean} maps every typo to {@code false}, which silently
     * disables features — {@code dashboard.enabled=ture} must fail, not turn it off.
     */
    private static void applyBoolean(Map<String, String> values, String key, BooleanSetter setter) {
        String value = values.get(key);
        if (value == null) {
            return;
        }
        if ("true".equalsIgnoreCase(value)) {
            setter.set(true);
        } else if ("false".equalsIgnoreCase(value)) {
            setter.set(false);
        } else {
            throw new ConfigurationException(key, "must be 'true' or 'false' but was '" + value + "'");
        }
    }

    /** Setter shape for text settings. */
    private interface StringSetter {
        void set(String value);
    }

    /** Setter shape for {@code int} settings. */
    private interface IntSetter {
        void set(int value);
    }

    /** Setter shape for {@code long} settings. */
    private interface LongSetter {
        void set(long value);
    }

    /** Setter shape for {@code double} settings. */
    private interface DoubleSetter {
        void set(double value);
    }

    /** Setter shape for {@code boolean} settings. */
    private interface BooleanSetter {
        void set(boolean value);
    }
}
