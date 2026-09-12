package com.example.pipeline.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link PipelineConfig} and its builder.
 *
 * <p>Every test here asserts on {@link ConfigurationException#key()} rather than on the
 * message text. The key is the contract — it is what the operator has to go and change,
 * and it is what the {@code --help} output lists. The wording around it is prose and
 * will be reworded.
 *
 * <p>The three cross-field rules are the interesting ones, because each of them describes
 * a configuration that is individually plausible and jointly broken: a producer that
 * never stops, a stage that cannot notice a stop flag inside the grace period, and an
 * unauthenticated mutation endpoint. None of the three would fail at startup without
 * this validation; all three would fail much later, and much less legibly.
 */
class PipelineConfigTest {

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("build with no overrides at all")
        void defaultsAreValid() {
            // If the defaults did not satisfy their own validation, every test and every
            // first run of the jar would fail on configuration rather than on the code.
            PipelineConfig config = assertDoesNotThrow(PipelineConfig::defaults);

            assertEquals("dev", config.env());
            assertEquals(100_000L, config.eventCount());
            assertEquals(128, config.batchSize());
            assertEquals(4, config.consumerThreads());
            assertEquals(64, config.queueCapacity());
            assertFalse(config.httpEnabled(), "the control plane must be opt-in");
            assertFalse(config.jdbcEnabled(), "persistence must be opt-in");
        }

        @Test
        @DisplayName("the Duration accessors agree with the raw numbers")
        void durationAccessorsAgree() {
            PipelineConfig config = PipelineConfig.builder()
                    .durationSeconds(90L)
                    .pollTimeoutMillis(250L)
                    .shutdownTimeoutSeconds(30L)
                    .dashboardIntervalMillis(500L)
                    .build();

            assertEquals(Duration.ofSeconds(90), config.maxDuration());
            assertEquals(Duration.ofMillis(250), config.pollTimeout());
            assertEquals(Duration.ofSeconds(30), config.shutdownTimeout());
            assertEquals(Duration.ofMillis(500), config.dashboardInterval());
        }

        /**
         * {@code toBuilder} exists for exactly the pattern used throughout this suite:
         * take a valid configuration and change one thing. A copy that dropped a field
         * would make every such test quietly run against a default.
         */
        @Test
        @DisplayName("toBuilder round-trips every field")
        void toBuilderRoundTrips() {
            PipelineConfig original = PipelineConfig.builder()
                    .env("staging")
                    .eventCount(0L)
                    .eventsPerSecond(0L)
                    .durationSeconds(300L)
                    .batchSize(64)
                    .sensorCount(32)
                    .randomSeed(7L)
                    .queueCapacity(32)
                    .consumerThreads(8)
                    .filterThreshold(75.5d)
                    .aggregationParallelism(6)
                    .sequentialThreshold(256)
                    .shutdownTimeoutSeconds(45L)
                    .pollTimeoutMillis(100L)
                    .dashboardEnabled(false)
                    .dashboardIntervalMillis(250L)
                    .logLevel("FINE")
                    .httpEnabled(true)
                    .httpPort(9090)
                    .httpToken("0123456789abcdef0123")
                    .jdbcUrl("jdbc:postgresql://localhost:5432/pipeline")
                    .jdbcUser("pipeline")
                    .jdbcPassword("secret")
                    .outputDir("./output/staging")
                    .build();

            // Compared through toString(), which covers all 24 fields in one assertion
            // and would fail if toBuilder dropped any single one of them.
            assertEquals(original.toString(), original.toBuilder().build().toString());
        }
    }

    @Nested
    @DisplayName("range validation")
    class Ranges {

        @ParameterizedTest
        @CsvSource({
            "pipeline.batch.size,               0",
            "pipeline.sensor.count,             0",
            "pipeline.queue.capacity,           0",
            "pipeline.consumer.threads,         0",
            "pipeline.sequential.threshold,     0",
            "pipeline.shutdown.timeout.seconds, 0",
            "pipeline.poll.timeout.millis,      0",
            "pipeline.dashboard.interval.millis,0",
        })
        @DisplayName("a value below the minimum fails, naming its own key")
        void belowMinimumFails(String key, int value) {
            PipelineConfig.Builder builder = PipelineConfig.builder();
            switch (key) {
                case PipelineConfig.KEY_BATCH_SIZE -> builder.batchSize(value);
                case PipelineConfig.KEY_SENSOR_COUNT -> builder.sensorCount(value);
                case PipelineConfig.KEY_QUEUE_CAPACITY -> builder.queueCapacity(value);
                case PipelineConfig.KEY_CONSUMER_THREADS -> builder.consumerThreads(value);
                case PipelineConfig.KEY_SEQUENTIAL_THRESHOLD -> builder.sequentialThreshold(value);
                case PipelineConfig.KEY_SHUTDOWN_TIMEOUT_SECONDS -> builder.shutdownTimeoutSeconds(value);
                case PipelineConfig.KEY_POLL_TIMEOUT_MILLIS -> builder.pollTimeoutMillis(value);
                case PipelineConfig.KEY_DASHBOARD_INTERVAL_MILLIS -> builder.dashboardIntervalMillis(value);
                default -> throw new AssertionError("unhandled key in the test data: " + key);
            }

            assertEquals(key, assertThrows(ConfigurationException.class, builder::build).key());
        }

        @ParameterizedTest
        @ValueSource(longs = {-1L, Long.MIN_VALUE})
        @DisplayName("a negative event count is rejected")
        void negativeEventCountFails(long value) {
            ConfigurationException thrown = assertThrows(ConfigurationException.class,
                    () -> PipelineConfig.builder().eventCount(value).build());
            assertEquals(PipelineConfig.KEY_EVENT_COUNT, thrown.key());
        }

        @Test
        @DisplayName("a blank env, log level or output dir is rejected")
        void blankTextFails() {
            assertEquals(PipelineConfig.KEY_ENV,
                    assertThrows(ConfigurationException.class,
                            () -> PipelineConfig.builder().env("  ").build()).key());
            assertEquals(PipelineConfig.KEY_LOG_LEVEL,
                    assertThrows(ConfigurationException.class,
                            () -> PipelineConfig.builder().logLevel("").build()).key());
            assertEquals(PipelineConfig.KEY_OUTPUT_DIR,
                    assertThrows(ConfigurationException.class,
                            () -> PipelineConfig.builder().outputDir(null).build()).key());
        }

        @ParameterizedTest
        @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
        @DisplayName("a non-finite filter threshold is rejected")
        void nonFiniteThresholdFails(double value) {
            // NaN here would make every comparison false and reject the entire run
            // without a single error being logged.
            assertEquals(PipelineConfig.KEY_FILTER_THRESHOLD,
                    assertThrows(ConfigurationException.class,
                            () -> PipelineConfig.builder().filterThreshold(value).build()).key());
        }

        @Test
        @DisplayName("zero is legal where it means 'unbounded'")
        void zeroIsLegalWhereItMeansUnbounded() {
            // Three settings use 0 as a sentinel rather than as a bad value, and
            // conflating those with "must be positive" is an easy validation bug.
            PipelineConfig config = PipelineConfig.builder()
                    .eventsPerSecond(0L)          // unthrottled
                    .aggregationParallelism(0)    // = availableProcessors()
                    .durationSeconds(0L)          // no time limit (event count bounds it)
                    .eventCount(1_000L)
                    .build();

            assertEquals(0L, config.eventsPerSecond());
            assertEquals(Runtime.getRuntime().availableProcessors(), config.effectiveParallelism());
            assertEquals(Duration.ZERO, config.maxDuration());
        }
    }

    @Nested
    @DisplayName("cross-field rules")
    class CrossField {

        /**
         * With neither bound set the producer loops forever, so no poison pill is ever
         * emitted and the pipeline cannot terminate. There is no exception to catch and
         * no log line to notice — it simply never finishes.
         */
        @Test
        @DisplayName("zero event count and zero duration is rejected: the producer would never stop")
        void needsSomeStoppingCondition() {
            ConfigurationException thrown = assertThrows(ConfigurationException.class,
                    () -> PipelineConfig.builder().eventCount(0L).durationSeconds(0L).build());

            assertEquals(PipelineConfig.KEY_EVENT_COUNT, thrown.key());
            assertTrue(thrown.getMessage().contains(PipelineConfig.KEY_DURATION_SECONDS),
                    "the message should name the other half of the rule: " + thrown.getMessage());
        }

        @Test
        @DisplayName("either bound alone is enough")
        void eitherBoundAloneIsEnough() {
            assertDoesNotThrow(() -> PipelineConfig.builder().eventCount(1L).durationSeconds(0L).build());
            assertDoesNotThrow(() -> PipelineConfig.builder().eventCount(0L).durationSeconds(1L).build());
        }

        /**
         * A poll timeout at or above the shutdown budget means a stage can be inside a
         * single blocking poll for the whole grace period and never look at its stop
         * flag — so the drain times out and the run reports lost events for a pipeline
         * that was working correctly.
         */
        @Test
        @DisplayName("a poll timeout at or above the shutdown budget is rejected")
        void pollTimeoutMustFitInsideTheShutdownBudget() {
            ConfigurationException thrown = assertThrows(ConfigurationException.class,
                    () -> PipelineConfig.builder()
                            .shutdownTimeoutSeconds(1L)
                            .pollTimeoutMillis(1_000L)
                            .build());

            assertEquals(PipelineConfig.KEY_POLL_TIMEOUT_MILLIS, thrown.key());

            // Just inside the bound is fine -- the rule is >=, and pinning that here
            // stops a future "tighten it a bit" change from going unnoticed.
            assertDoesNotThrow(() -> PipelineConfig.builder()
                    .shutdownTimeoutSeconds(1L)
                    .pollTimeoutMillis(999L)
                    .build());
        }
    }

    @Nested
    @DisplayName("control plane")
    class ControlPlane {

        @Test
        @DisplayName("refuses to enable HTTP without a long enough token")
        void refusesWeakToken() {
            // The endpoint can retune a running pipeline. Starting it unauthenticated
            // because the token happened to be unset is not a convenience.
            assertEquals(PipelineConfig.KEY_HTTP_TOKEN,
                    assertThrows(ConfigurationException.class,
                            () -> PipelineConfig.builder().httpEnabled(true).build()).key());

            assertEquals(PipelineConfig.KEY_HTTP_TOKEN,
                    assertThrows(ConfigurationException.class,
                            () -> PipelineConfig.builder()
                                    .httpEnabled(true)
                                    .httpToken("123456789012345")
                                    .build()).key(),
                    "15 characters is one short of the minimum");
        }

        @Test
        @DisplayName("a token of only whitespace padding does not count")
        void tokenIsStrippedBeforeMeasuring() {
            // "        abc        " is 19 characters and 3 of secret.
            assertEquals(PipelineConfig.KEY_HTTP_TOKEN,
                    assertThrows(ConfigurationException.class,
                            () -> PipelineConfig.builder()
                                    .httpEnabled(true)
                                    .httpToken("        abc        ")
                                    .build()).key());
        }

        @Test
        @DisplayName("accepts a 16-character token")
        void acceptsMinimumLengthToken() {
            PipelineConfig config = PipelineConfig.builder()
                    .httpEnabled(true)
                    .httpToken("0123456789abcdef")
                    .build();

            assertTrue(config.httpEnabled());
            assertEquals(8080, config.httpPort());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 80, 1023, 65_536, -1})
        @DisplayName("a port outside 1024..65535 is rejected when HTTP is on")
        void rejectsPortOutsideRange(int port) {
            assertEquals(PipelineConfig.KEY_HTTP_PORT,
                    assertThrows(ConfigurationException.class,
                            () -> PipelineConfig.builder()
                                    .httpEnabled(true)
                                    .httpToken("0123456789abcdef")
                                    .httpPort(port)
                                    .build()).key());
        }

        /**
         * The port and token rules are conditional on the feature being on. Validating
         * them unconditionally would reject a perfectly good default configuration for a
         * server nobody asked to start.
         */
        @Test
        @DisplayName("port and token are not validated while HTTP is off")
        void rulesApplyOnlyWhenEnabled() {
            assertDoesNotThrow(() -> PipelineConfig.builder()
                    .httpEnabled(false)
                    .httpPort(80)
                    .httpToken("")
                    .build());
        }
    }

    @Nested
    @DisplayName("persistence")
    class Persistence {

        @Test
        @DisplayName("a JDBC url without a user is rejected")
        void urlRequiresUser() {
            assertEquals(PipelineConfig.KEY_JDBC_USER,
                    assertThrows(ConfigurationException.class,
                            () -> PipelineConfig.builder()
                                    .jdbcUrl("jdbc:postgresql://localhost:5432/pipeline")
                                    .build()).key());
        }

        @Test
        @DisplayName("jdbcEnabled follows the url, not the user or password")
        void jdbcEnabledFollowsTheUrl() {
            assertFalse(PipelineConfig.defaults().jdbcEnabled());
            assertTrue(PipelineConfig.builder()
                    .jdbcUrl("jdbc:postgresql://localhost:5432/pipeline")
                    .jdbcUser("pipeline")
                    .build()
                    .jdbcEnabled());
        }
    }

    @Nested
    @DisplayName("derived values")
    class Derived {

        /**
         * One pill per consumer, and the count is derived rather than configured. A
         * config key here would let the two numbers drift: too few pills hangs the
         * shutdown, too many means a pill is dequeued ahead of a batch that is then
         * never processed.
         */
        @ParameterizedTest
        @ValueSource(ints = {1, 4, 8, 64})
        @DisplayName("poisonPillCount always equals consumerThreads")
        void pillCountTracksConsumerThreads(int threads) {
            PipelineConfig config = PipelineConfig.builder().consumerThreads(threads).build();
            assertEquals(threads, config.poisonPillCount());
        }

        @Test
        @DisplayName("effectiveParallelism resolves 0 to the CPU count and otherwise passes through")
        void effectiveParallelismResolvesZero() {
            assertEquals(Runtime.getRuntime().availableProcessors(),
                    PipelineConfig.builder().aggregationParallelism(0).build().effectiveParallelism());
            assertEquals(3, PipelineConfig.builder().aggregationParallelism(3).build().effectiveParallelism());
        }
    }

    @Nested
    @DisplayName("toString")
    class Redaction {

        /**
         * A config dump is the first thing to reach for when a run misbehaves, which
         * means it ends up in logs, issue reports and CI output. It must be safe to paste.
         */
        @Test
        @DisplayName("redacts the token and the password, reporting only presence")
        void redactsSecrets() {
            String dump = PipelineConfig.builder()
                    .httpEnabled(true)
                    .httpToken("super-secret-token-value")
                    .jdbcUrl("jdbc:postgresql://localhost:5432/pipeline")
                    .jdbcUser("pipeline")
                    .jdbcPassword("hunter2")
                    .build()
                    .toString();

            assertFalse(dump.contains("super-secret-token-value"), dump);
            assertFalse(dump.contains("hunter2"), dump);
            assertTrue(dump.contains("token=(set)"), dump);
            assertTrue(dump.contains("password=(set)"), dump);
            // The url and user are not secrets and are worth having in a log line.
            assertTrue(dump.contains("jdbc:postgresql://localhost:5432/pipeline"), dump);
        }

        @Test
        @DisplayName("an unset secret reads as (unset), not as an empty string")
        void reportsUnsetSecrets() {
            String dump = PipelineConfig.defaults().toString();
            assertTrue(dump.contains("token=(unset)"), dump);
            assertTrue(dump.contains("password=(unset)"), dump);
        }
    }
}
