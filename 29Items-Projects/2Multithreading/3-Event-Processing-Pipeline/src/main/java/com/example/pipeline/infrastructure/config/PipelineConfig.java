package com.example.pipeline.infrastructure.config;

import java.time.Duration;
import java.util.Locale;

/**
 * Immutable, validated settings for one pipeline run.
 *
 * <p><strong>Validated once, at the edge.</strong> Every constraint is checked in
 * {@link Builder#build()}, so the rest of the codebase never has to ask whether a
 * thread count is positive. A bad value fails before a single thread starts, with a
 * message naming the offending key — the difference between "fix
 * {@code pipeline.queue.capacity}" and a deadlock ten minutes into a soak run.
 *
 * <p><strong>Configuration is the real attack surface here</strong> (see
 * {@code docs/ARCHITECTURE.md} §2.5): there is no network input by default, so the
 * values below are the only untrusted data the process reads. Hence the range checks
 * rather than a permissive parse.
 *
 * <p>{@link #toString()} redacts secrets so a config dump can safely go to a log.
 */
public final class PipelineConfig {

    /** Property key for {@link #env()}. */
    public static final String KEY_ENV = "pipeline.env";

    /** Property key for {@link #eventCount()}. */
    public static final String KEY_EVENT_COUNT = "pipeline.event.count";

    /** Property key for {@link #eventsPerSecond()}. */
    public static final String KEY_EVENTS_PER_SECOND = "pipeline.events.per.second";

    /** Property key for {@link #durationSeconds()}. */
    public static final String KEY_DURATION_SECONDS = "pipeline.duration.seconds";

    /** Property key for {@link #batchSize()}. */
    public static final String KEY_BATCH_SIZE = "pipeline.batch.size";

    /** Property key for {@link #sensorCount()}. */
    public static final String KEY_SENSOR_COUNT = "pipeline.sensor.count";

    /** Property key for {@link #randomSeed()}. */
    public static final String KEY_RANDOM_SEED = "pipeline.random.seed";

    /** Property key for {@link #queueCapacity()}. */
    public static final String KEY_QUEUE_CAPACITY = "pipeline.queue.capacity";

    /** Property key for {@link #consumerThreads()}. */
    public static final String KEY_CONSUMER_THREADS = "pipeline.consumer.threads";

    /** Property key for {@link #filterThreshold()}. */
    public static final String KEY_FILTER_THRESHOLD = "pipeline.filter.threshold";

    /** Property key for {@link #aggregationParallelism()}. */
    public static final String KEY_AGGREGATION_PARALLELISM = "pipeline.aggregation.parallelism";

    /** Property key for {@link #sequentialThreshold()}. */
    public static final String KEY_SEQUENTIAL_THRESHOLD = "pipeline.sequential.threshold";

    /** Property key for {@link #shutdownTimeoutSeconds()}. */
    public static final String KEY_SHUTDOWN_TIMEOUT_SECONDS = "pipeline.shutdown.timeout.seconds";

    /** Property key for {@link #pollTimeoutMillis()}. */
    public static final String KEY_POLL_TIMEOUT_MILLIS = "pipeline.poll.timeout.millis";

    /** Property key for {@link #dashboardEnabled()}. */
    public static final String KEY_DASHBOARD_ENABLED = "pipeline.dashboard.enabled";

    /** Property key for {@link #dashboardIntervalMillis()}. */
    public static final String KEY_DASHBOARD_INTERVAL_MILLIS = "pipeline.dashboard.interval.millis";

    /** Property key for {@link #logLevel()}. */
    public static final String KEY_LOG_LEVEL = "pipeline.log.level";

    /** Property key for {@link #httpEnabled()}. */
    public static final String KEY_HTTP_ENABLED = "pipeline.http.enabled";

    /** Property key for {@link #httpPort()}. */
    public static final String KEY_HTTP_PORT = "pipeline.http.port";

    /** Property key for {@link #httpToken()}; redacted in {@link #toString()}. */
    public static final String KEY_HTTP_TOKEN = "pipeline.http.token";

    /** Property key for {@link #jdbcUrl()}. */
    public static final String KEY_JDBC_URL = "pipeline.jdbc.url";

    /** Property key for {@link #jdbcUser()}. */
    public static final String KEY_JDBC_USER = "pipeline.jdbc.user";

    /** Property key for {@link #jdbcPassword()}; redacted in {@link #toString()}. */
    public static final String KEY_JDBC_PASSWORD = "pipeline.jdbc.password";

    /** Property key for {@link #outputDir()}. */
    public static final String KEY_OUTPUT_DIR = "pipeline.output.dir";

    /** Shortest token accepted when the control plane is enabled. */
    private static final int MIN_TOKEN_LENGTH = 16;

    /** Lowest non-privileged TCP port; binding below this needs elevation on most systems. */
    private static final int MIN_PORT = 1024;

    private static final int MAX_PORT = 65_535;

    private final String env;
    private final long eventCount;
    private final long eventsPerSecond;
    private final long durationSeconds;
    private final int batchSize;
    private final int sensorCount;
    private final long randomSeed;
    private final int queueCapacity;
    private final int consumerThreads;
    private final double filterThreshold;
    private final int aggregationParallelism;
    private final int sequentialThreshold;
    private final long shutdownTimeoutSeconds;
    private final long pollTimeoutMillis;
    private final boolean dashboardEnabled;
    private final long dashboardIntervalMillis;
    private final String logLevel;
    private final boolean httpEnabled;
    private final int httpPort;
    private final String httpToken;
    private final String jdbcUrl;
    private final String jdbcUser;
    private final String jdbcPassword;
    private final String outputDir;

    private PipelineConfig(Builder builder) {
        this.env = builder.env;
        this.eventCount = builder.eventCount;
        this.eventsPerSecond = builder.eventsPerSecond;
        this.durationSeconds = builder.durationSeconds;
        this.batchSize = builder.batchSize;
        this.sensorCount = builder.sensorCount;
        this.randomSeed = builder.randomSeed;
        this.queueCapacity = builder.queueCapacity;
        this.consumerThreads = builder.consumerThreads;
        this.filterThreshold = builder.filterThreshold;
        this.aggregationParallelism = builder.aggregationParallelism;
        this.sequentialThreshold = builder.sequentialThreshold;
        this.shutdownTimeoutSeconds = builder.shutdownTimeoutSeconds;
        this.pollTimeoutMillis = builder.pollTimeoutMillis;
        this.dashboardEnabled = builder.dashboardEnabled;
        this.dashboardIntervalMillis = builder.dashboardIntervalMillis;
        this.logLevel = builder.logLevel;
        this.httpEnabled = builder.httpEnabled;
        this.httpPort = builder.httpPort;
        this.httpToken = builder.httpToken;
        this.jdbcUrl = builder.jdbcUrl;
        this.jdbcUser = builder.jdbcUser;
        this.jdbcPassword = builder.jdbcPassword;
        this.outputDir = builder.outputDir;
    }

    /** A builder pre-filled with the documented defaults. */
    public static Builder builder() {
        return new Builder();
    }

    /** The defaults, unmodified — what a run with no configuration at all uses. */
    public static PipelineConfig defaults() {
        return new Builder().build();
    }

    /** Profile name: {@code dev}, {@code staging} or {@code prod}. */
    public String env() {
        return env;
    }

    /** Total events to generate; {@code 0} means "until the duration elapses". */
    public long eventCount() {
        return eventCount;
    }

    /** Target offered load; {@code 0} means unbounded (saturation test). */
    public long eventsPerSecond() {
        return eventsPerSecond;
    }

    /** Wall-clock cap in seconds; {@code 0} means no time limit. */
    public long durationSeconds() {
        return durationSeconds;
    }

    /** Events per queue message. */
    public int batchSize() {
        return batchSize;
    }

    /** Number of distinct synthetic sensors. */
    public int sensorCount() {
        return sensorCount;
    }

    /** Generator seed — the same seed replays the same event stream. */
    public long randomSeed() {
        return randomSeed;
    }

    /** Capacity, in messages, of each inter-stage queue. */
    public int queueCapacity() {
        return queueCapacity;
    }

    /** Fixed thread pool size for the filter stage. */
    public int consumerThreads() {
        return consumerThreads;
    }

    /** Events at or below this value are rejected. */
    public double filterThreshold() {
        return filterThreshold;
    }

    /** Configured fork/join parallelism; {@code 0} means {@code availableProcessors()}. */
    public int aggregationParallelism() {
        return aggregationParallelism;
    }

    /** Fork/join cutoff, in events. */
    public int sequentialThreshold() {
        return sequentialThreshold;
    }

    /** Grace period for the drain, in seconds. */
    public long shutdownTimeoutSeconds() {
        return shutdownTimeoutSeconds;
    }

    /** Stage-loop poll timeout, in milliseconds. */
    public long pollTimeoutMillis() {
        return pollTimeoutMillis;
    }

    /** Whether the live console dashboard runs. */
    public boolean dashboardEnabled() {
        return dashboardEnabled;
    }

    /** Dashboard refresh interval, in milliseconds. */
    public long dashboardIntervalMillis() {
        return dashboardIntervalMillis;
    }

    /** {@code java.util.logging} level name. */
    public String logLevel() {
        return logLevel;
    }

    /** Whether the localhost control plane is started. */
    public boolean httpEnabled() {
        return httpEnabled;
    }

    /** Control-plane port. */
    public int httpPort() {
        return httpPort;
    }

    /** Control-plane shared secret; empty when the control plane is off. */
    public String httpToken() {
        return httpToken;
    }

    /** JDBC URL; empty selects the in-memory repository. */
    public String jdbcUrl() {
        return jdbcUrl;
    }

    /** JDBC user; empty when {@link #jdbcUrl()} is empty. */
    public String jdbcUser() {
        return jdbcUser;
    }

    /** JDBC password; empty when {@link #jdbcUrl()} is empty. */
    public String jdbcPassword() {
        return jdbcPassword;
    }

    /** Root directory for file sinks. */
    public String outputDir() {
        return outputDir;
    }

    /** {@link #durationSeconds()} as a {@code Duration}; {@link Duration#ZERO} means unlimited. */
    public Duration maxDuration() {
        return Duration.ofSeconds(durationSeconds);
    }

    /** {@link #pollTimeoutMillis()} as a {@code Duration}. */
    public Duration pollTimeout() {
        return Duration.ofMillis(pollTimeoutMillis);
    }

    /** {@link #shutdownTimeoutSeconds()} as a {@code Duration}. */
    public Duration shutdownTimeout() {
        return Duration.ofSeconds(shutdownTimeoutSeconds);
    }

    /** {@link #dashboardIntervalMillis()} as a {@code Duration}. */
    public Duration dashboardInterval() {
        return Duration.ofMillis(dashboardIntervalMillis);
    }

    /** Fork/join parallelism actually used, resolving {@code 0} to the CPU count. */
    public int effectiveParallelism() {
        return aggregationParallelism > 0 ? aggregationParallelism : Runtime.getRuntime().availableProcessors();
    }

    /**
     * Poison pills the producer must emit: exactly one per filter consumer.
     *
     * <p>Derived rather than configured on purpose — a config key here would let the
     * two numbers drift apart, and any mismatch is either a hang (too few) or a lost
     * batch (too many). See {@code docs/ARCHITECTURE.md} §2.3.
     */
    public int poisonPillCount() {
        return consumerThreads;
    }

    /** {@code true} when a JDBC repository is configured. */
    public boolean jdbcEnabled() {
        return !jdbcUrl.isBlank();
    }

    /** A copy of this configuration as a builder, for tests that tweak one value. */
    public Builder toBuilder() {
        return new Builder()
                .env(env)
                .eventCount(eventCount)
                .eventsPerSecond(eventsPerSecond)
                .durationSeconds(durationSeconds)
                .batchSize(batchSize)
                .sensorCount(sensorCount)
                .randomSeed(randomSeed)
                .queueCapacity(queueCapacity)
                .consumerThreads(consumerThreads)
                .filterThreshold(filterThreshold)
                .aggregationParallelism(aggregationParallelism)
                .sequentialThreshold(sequentialThreshold)
                .shutdownTimeoutSeconds(shutdownTimeoutSeconds)
                .pollTimeoutMillis(pollTimeoutMillis)
                .dashboardEnabled(dashboardEnabled)
                .dashboardIntervalMillis(dashboardIntervalMillis)
                .logLevel(logLevel)
                .httpEnabled(httpEnabled)
                .httpPort(httpPort)
                .httpToken(httpToken)
                .jdbcUrl(jdbcUrl)
                .jdbcUser(jdbcUser)
                .jdbcPassword(jdbcPassword)
                .outputDir(outputDir);
    }

    /** Human-readable summary with secrets redacted — safe to log verbatim. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "PipelineConfig[env=%s events=%d rate=%d/s duration=%ds batch=%d sensors=%d seed=%d "
                        + "queueCapacity=%d consumers=%d threshold=%.2f parallelism=%d(effective=%d) cutoff=%d "
                        + "shutdown=%ds poll=%dms dashboard=%b/%dms log=%s http=%b:%d token=%s "
                        + "jdbc=%s user=%s password=%s out=%s]",
                env, eventCount, eventsPerSecond, durationSeconds, batchSize, sensorCount, randomSeed,
                queueCapacity, consumerThreads, filterThreshold, aggregationParallelism, effectiveParallelism(),
                sequentialThreshold, shutdownTimeoutSeconds, pollTimeoutMillis, dashboardEnabled,
                dashboardIntervalMillis, logLevel, httpEnabled, httpPort, redact(httpToken),
                jdbcUrl.isBlank() ? "(none)" : jdbcUrl, jdbcUser.isBlank() ? "(none)" : jdbcUser,
                redact(jdbcPassword), outputDir);
    }

    /** Reports presence, never content: a redacted log line must not leak the length either. */
    private static String redact(String secret) {
        return secret == null || secret.isBlank() ? "(unset)" : "(set)";
    }

    /**
     * Mutable builder; {@link #build()} performs all validation.
     *
     * <p>A builder rather than a 24-argument constructor because positional arguments
     * of the same type are exactly how {@code consumerThreads} ends up in
     * {@code queueCapacity}, and the compiler cannot help.
     */
    public static final class Builder {

        private String env = "dev";
        private long eventCount = 100_000L;
        private long eventsPerSecond = 50_000L;
        private long durationSeconds;
        private int batchSize = 128;
        private int sensorCount = 16;
        private long randomSeed = 42L;
        private int queueCapacity = 64;
        private int consumerThreads = 4;
        private double filterThreshold = 50.0d;
        private int aggregationParallelism;
        private int sequentialThreshold = 512;
        private long shutdownTimeoutSeconds = 30L;
        private long pollTimeoutMillis = 250L;
        private boolean dashboardEnabled = true;
        private long dashboardIntervalMillis = 500L;
        private String logLevel = "INFO";
        private boolean httpEnabled;
        private int httpPort = 8080;
        private String httpToken = "";
        private String jdbcUrl = "";
        private String jdbcUser = "";
        private String jdbcPassword = "";
        private String outputDir = "./output";

        private Builder() {
        }

        /** Sets the profile name. */
        public Builder env(String value) {
            this.env = value;
            return this;
        }

        /** Sets the event budget. */
        public Builder eventCount(long value) {
            this.eventCount = value;
            return this;
        }

        /** Sets the target rate. */
        public Builder eventsPerSecond(long value) {
            this.eventsPerSecond = value;
            return this;
        }

        /** Sets the wall-clock cap. */
        public Builder durationSeconds(long value) {
            this.durationSeconds = value;
            return this;
        }

        /** Sets the batch size. */
        public Builder batchSize(int value) {
            this.batchSize = value;
            return this;
        }

        /** Sets the sensor count. */
        public Builder sensorCount(int value) {
            this.sensorCount = value;
            return this;
        }

        /** Sets the generator seed. */
        public Builder randomSeed(long value) {
            this.randomSeed = value;
            return this;
        }

        /** Sets the per-queue capacity. */
        public Builder queueCapacity(int value) {
            this.queueCapacity = value;
            return this;
        }

        /** Sets the filter consumer count. */
        public Builder consumerThreads(int value) {
            this.consumerThreads = value;
            return this;
        }

        /** Sets the filter threshold. */
        public Builder filterThreshold(double value) {
            this.filterThreshold = value;
            return this;
        }

        /** Sets the fork/join parallelism; {@code 0} resolves to the CPU count. */
        public Builder aggregationParallelism(int value) {
            this.aggregationParallelism = value;
            return this;
        }

        /** Sets the fork/join cutoff. */
        public Builder sequentialThreshold(int value) {
            this.sequentialThreshold = value;
            return this;
        }

        /** Sets the drain grace period. */
        public Builder shutdownTimeoutSeconds(long value) {
            this.shutdownTimeoutSeconds = value;
            return this;
        }

        /** Sets the stage-loop poll timeout. */
        public Builder pollTimeoutMillis(long value) {
            this.pollTimeoutMillis = value;
            return this;
        }

        /** Enables or disables the console dashboard. */
        public Builder dashboardEnabled(boolean value) {
            this.dashboardEnabled = value;
            return this;
        }

        /** Sets the dashboard refresh interval. */
        public Builder dashboardIntervalMillis(long value) {
            this.dashboardIntervalMillis = value;
            return this;
        }

        /** Sets the logging level name. */
        public Builder logLevel(String value) {
            this.logLevel = value;
            return this;
        }

        /** Enables or disables the control plane. */
        public Builder httpEnabled(boolean value) {
            this.httpEnabled = value;
            return this;
        }

        /** Sets the control-plane port. */
        public Builder httpPort(int value) {
            this.httpPort = value;
            return this;
        }

        /** Sets the control-plane shared secret. */
        public Builder httpToken(String value) {
            this.httpToken = value;
            return this;
        }

        /** Sets the JDBC URL. */
        public Builder jdbcUrl(String value) {
            this.jdbcUrl = value;
            return this;
        }

        /** Sets the JDBC user. */
        public Builder jdbcUser(String value) {
            this.jdbcUser = value;
            return this;
        }

        /** Sets the JDBC password. */
        public Builder jdbcPassword(String value) {
            this.jdbcPassword = value;
            return this;
        }

        /** Sets the file-sink output directory. */
        public Builder outputDir(String value) {
            this.outputDir = value;
            return this;
        }

        /**
         * Validates every setting and returns the immutable configuration.
         *
         * @throws ConfigurationException on the first violated constraint, naming the key
         */
        public PipelineConfig build() {
            requireText(KEY_ENV, env);
            requireText(KEY_LOG_LEVEL, logLevel);
            requireText(KEY_OUTPUT_DIR, outputDir);
            requireNonNegative(KEY_EVENT_COUNT, eventCount);
            requireNonNegative(KEY_EVENTS_PER_SECOND, eventsPerSecond);
            requireNonNegative(KEY_DURATION_SECONDS, durationSeconds);
            requireAtLeast(KEY_BATCH_SIZE, batchSize, 1);
            requireAtLeast(KEY_SENSOR_COUNT, sensorCount, 1);
            requireAtLeast(KEY_QUEUE_CAPACITY, queueCapacity, 1);
            requireAtLeast(KEY_CONSUMER_THREADS, consumerThreads, 1);
            requireNonNegative(KEY_AGGREGATION_PARALLELISM, aggregationParallelism);
            requireAtLeast(KEY_SEQUENTIAL_THRESHOLD, sequentialThreshold, 1);
            requireAtLeast(KEY_SHUTDOWN_TIMEOUT_SECONDS, shutdownTimeoutSeconds, 1L);
            requireAtLeast(KEY_POLL_TIMEOUT_MILLIS, pollTimeoutMillis, 1L);
            requireAtLeast(KEY_DASHBOARD_INTERVAL_MILLIS, dashboardIntervalMillis, 1L);
            if (!Double.isFinite(filterThreshold)) {
                throw new ConfigurationException(KEY_FILTER_THRESHOLD, "must be a finite number");
            }
            // Without one of these the producer never returns and no pill is ever emitted.
            if (eventCount == 0L && durationSeconds == 0L) {
                throw new ConfigurationException(KEY_EVENT_COUNT,
                        "either " + KEY_EVENT_COUNT + " or " + KEY_DURATION_SECONDS
                                + " must be non-zero, otherwise the producer never stops");
            }
            // A poll timeout at or above the shutdown budget means a stage can miss the
            // whole grace period in a single blocking poll.
            if (pollTimeoutMillis >= shutdownTimeoutSeconds * 1_000L) {
                throw new ConfigurationException(KEY_POLL_TIMEOUT_MILLIS,
                        "must be well below " + KEY_SHUTDOWN_TIMEOUT_SECONDS + " (" + shutdownTimeoutSeconds
                                + "s), otherwise a stage cannot notice the stop flag in time");
            }
            if (httpEnabled) {
                if (httpPort < MIN_PORT || httpPort > MAX_PORT) {
                    throw new ConfigurationException(KEY_HTTP_PORT,
                            "must be between " + MIN_PORT + " and " + MAX_PORT + " but was " + httpPort);
                }
                // Refusing to start beats starting an unauthenticated mutation endpoint.
                if (httpToken == null || httpToken.strip().length() < MIN_TOKEN_LENGTH) {
                    throw new ConfigurationException(KEY_HTTP_TOKEN,
                            "must be at least " + MIN_TOKEN_LENGTH
                                    + " characters when " + KEY_HTTP_ENABLED + "=true");
                }
            }
            if (!jdbcUrl.isBlank() && jdbcUser.isBlank()) {
                throw new ConfigurationException(KEY_JDBC_USER, "is required when " + KEY_JDBC_URL + " is set");
            }
            return new PipelineConfig(this);
        }

        private static void requireText(String key, String value) {
            if (value == null || value.isBlank()) {
                throw new ConfigurationException(key, "must not be blank");
            }
        }

        private static void requireNonNegative(String key, long value) {
            requireAtLeast(key, value, 0L);
        }

        private static void requireAtLeast(String key, long value, long minimum) {
            if (value < minimum) {
                throw new ConfigurationException(key, "must be >= " + minimum + " but was " + value);
            }
        }
    }
}
