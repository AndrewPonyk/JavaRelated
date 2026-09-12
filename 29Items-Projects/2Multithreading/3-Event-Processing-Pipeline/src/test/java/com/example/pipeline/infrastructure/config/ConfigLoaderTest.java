package com.example.pipeline.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The five-layer precedence chain, tested without touching the real environment.
 *
 * <p>{@link ConfigLoader} takes its environment as a {@link java.util.function.UnaryOperator}
 * and its profile directory as a {@link Path} precisely so this test can exist: a fake map
 * plus a {@link TempDir} pins every layer boundary, and nothing here depends on how the JVM
 * was launched or on a real {@code PIPELINE_*} variable being absent from the developer's
 * shell.
 *
 * <p>Layer 2 is the committed {@code application.properties}, which surefire puts on the
 * classpath for real. That is deliberate: the tests in {@link Precedence} assert against the
 * baseline values that ship in the jar, so a careless edit to that file — changing
 * {@code batch.size} without meaning to — fails here rather than silently changing what
 * every operator gets by default.
 *
 * <p>The tests worth reading are {@link Precedence#cliBeatsEverything()}, which is the
 * property an operator relies on at 3 a.m., and
 * {@link Profiles#theProfileIsChosenBeforeTheFileItSelectsIsRead()}, which pins the one
 * ordering subtlety in {@code load}: the profile has to be resolved from the CLI and the
 * environment <em>before</em> layer 3 can be read at all, because it names the file.
 */
@Timeout(20)
@DisplayName("ConfigLoader")
class ConfigLoaderTest {

    /** Values that ship in {@code src/main/resources/application.properties}. */
    private static final int BASELINE_BATCH_SIZE = 128;
    private static final int BASELINE_QUEUE_CAPACITY = 64;

    @TempDir
    Path configDir;

    private Map<String, String> env;

    @BeforeEach
    void setUp() {
        env = new HashMap<>();
    }

    /** Loader over the fake environment and the temporary profile directory. */
    private ConfigLoader loader() {
        return new ConfigLoader(env::get, configDir);
    }

    private void writeProfile(String profile, String... lines) throws IOException {
        Files.write(configDir.resolve("application-" + profile + ".properties"), List.of(lines));
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("both collaborators are required")
        void nullsAreRejected() {
            assertThrows(NullPointerException.class, () -> new ConfigLoader(null, configDir));
            assertThrows(NullPointerException.class, () -> new ConfigLoader(env::get, null));
        }

        @Test
        @DisplayName("the source names are the ones the README and .env.example document")
        void publicConstantsAreStable() {
            // Documented in three places, so a rename has to be deliberate. The no-arg loader
            // is constructed but never loaded here: load() on it would read whatever PIPELINE_*
            // variables the developer's shell happens to carry, and stop being a test.
            assertEquals("application.properties", ConfigLoader.CLASSPATH_RESOURCE);
            assertEquals("config", ConfigLoader.CONFIG_DIR);
            assertEquals("PIPELINE_", ConfigLoader.ENV_PREFIX);
            assertNotNull(new ConfigLoader());
        }

        @Test
        @DisplayName("load rejects a null override map rather than treating it as empty")
        void nullOverridesAreRejected() {
            assertThrows(NullPointerException.class, () -> loader().load(null));
        }
    }

    @Nested
    @DisplayName("known keys")
    class KnownKeys {

        @Test
        @DisplayName("all 24 settings are listed, and every one starts with the shared prefix")
        void everyKeyIsListed() {
            List<String> keys = ConfigLoader.knownKeys();
            assertEquals(24, keys.size(), keys.toString());
            for (String key : keys) {
                assertTrue(key.startsWith("pipeline."), key + " breaks the single-vocabulary rule");
            }
            assertEquals(keys.size(), Set.copyOf(keys).size(), "a duplicated key would shadow itself");
        }

        @Test
        @DisplayName("the list is declaration-ordered, which is also --help's order")
        void orderIsStable() {
            List<String> keys = ConfigLoader.knownKeys();
            assertEquals(PipelineConfig.KEY_ENV, keys.get(0), "env comes first: it selects the profile");
            assertEquals(PipelineConfig.KEY_OUTPUT_DIR, keys.get(keys.size() - 1));
        }

        @Test
        @DisplayName("the list cannot be mutated by a caller building --help text")
        void listIsUnmodifiable() {
            assertThrows(UnsupportedOperationException.class,
                    () -> ConfigLoader.knownKeys().add("pipeline.extra"));
        }

        @Test
        @DisplayName("every key the loader applies is also a key it recognises")
        void appliedKeysAreKnownKeys() {
            // The two lists are maintained by hand in load() and KNOWN_KEYS. A key present in
            // one but not the other is either unsettable or unrejectable, and both are silent.
            List<String> keys = ConfigLoader.knownKeys();
            for (String key : List.of(PipelineConfig.KEY_HTTP_TOKEN, PipelineConfig.KEY_JDBC_PASSWORD,
                    PipelineConfig.KEY_AGGREGATION_PARALLELISM, PipelineConfig.KEY_DURATION_SECONDS,
                    PipelineConfig.KEY_RANDOM_SEED, PipelineConfig.KEY_LOG_LEVEL)) {
                assertTrue(keys.contains(key), key);
            }
        }
    }

    @Nested
    @DisplayName("envVarFor()")
    class EnvVarNaming {

        @ParameterizedTest
        @DisplayName("a property key maps mechanically onto an upper-snake variable name")
        @CsvSource({
            "pipeline.queue.capacity, PIPELINE_QUEUE_CAPACITY",
            "pipeline.env, PIPELINE_ENV",
            "pipeline.http.token, PIPELINE_HTTP_TOKEN",
            "pipeline.jdbc.password, PIPELINE_JDBC_PASSWORD",
            "pipeline.shutdown.timeout.seconds, PIPELINE_SHUTDOWN_TIMEOUT_SECONDS",
        })
        void keysMapToVariables(String key, String expected) {
            assertEquals(expected, ConfigLoader.envVarFor(key));
        }

        @Test
        @DisplayName("a key without the pipeline. prefix still gets exactly one prefix")
        void unprefixedKeyIsNotDoublePrefixed() {
            // The prefix is stripped before it is re-added, so a caller passing the short form
            // cannot produce PIPELINE_PIPELINE_*.
            assertEquals("PIPELINE_BATCH_SIZE", ConfigLoader.envVarFor("batch.size"));
        }

        @Test
        @DisplayName("only a leading pipeline. is stripped")
        void innerOccurrenceSurvives() {
            assertEquals("PIPELINE_X_PIPELINE_Y", ConfigLoader.envVarFor("pipeline.x.pipeline.y"));
        }

        @Test
        @DisplayName("every known key has a distinct variable name")
        void namesDoNotCollide() {
            // Two keys mapping to one variable would make one of them unsettable from a
            // container, and the collision would be invisible until someone tried.
            List<String> names = ConfigLoader.knownKeys().stream().map(ConfigLoader::envVarFor).toList();
            assertEquals(names.size(), Set.copyOf(names).size(), names.toString());
        }

        @Test
        @DisplayName("the case mapping is locale-independent")
        void upperCasingUsesRootLocale() {
            // A Turkish default locale upper-cases 'i' to a dotted capital, which would make
            // PIPELINE_SEQUENTIAL_THRESHOLD unreachable on a Turkish machine.
            assertTrue(ConfigLoader.envVarFor("pipeline.sequential.threshold").chars()
                    .allMatch(c -> c < 0x80));
        }
    }

    @Nested
    @DisplayName("precedence")
    class Precedence {

        @Test
        @DisplayName("with nothing overridden, the committed baseline wins over the compiled defaults")
        void classpathBaselineIsTheFloor() {
            PipelineConfig config = loader().load(Map.of());
            assertEquals(BASELINE_BATCH_SIZE, config.batchSize());
            assertEquals(BASELINE_QUEUE_CAPACITY, config.queueCapacity());
            assertEquals("dev", config.env());
        }

        @Test
        @DisplayName("a profile file beats the classpath baseline")
        void profileBeatsClasspath() throws IOException {
            writeProfile("dev", "pipeline.batch.size=256");
            assertEquals(256, loader().load(Map.of()).batchSize());
        }

        @Test
        @DisplayName("an environment variable beats the profile file")
        void environmentBeatsProfile() throws IOException {
            writeProfile("dev", "pipeline.batch.size=256");
            env.put("PIPELINE_BATCH_SIZE", "512");
            assertEquals(512, loader().load(Map.of()).batchSize());
        }

        /**
         * The property the whole ordering exists for: an operator on the box can always win
         * over an inherited environment and a checked-in file without editing either.
         */
        @Test
        @DisplayName("a CLI flag beats the environment, the profile and the baseline")
        void cliBeatsEverything() throws IOException {
            writeProfile("dev", "pipeline.batch.size=256");
            env.put("PIPELINE_BATCH_SIZE", "512");
            PipelineConfig config = loader().load(Map.of(PipelineConfig.KEY_BATCH_SIZE, "1024"));
            assertEquals(1024, config.batchSize());
        }

        @Test
        @DisplayName("layers merge per key rather than replacing each other wholesale")
        void layersMergeKeyByKey() throws IOException {
            writeProfile("dev", "pipeline.consumer.threads=8");
            env.put("PIPELINE_SENSOR_COUNT", "32");
            PipelineConfig config = loader().load(Map.of(PipelineConfig.KEY_QUEUE_CAPACITY, "256"));
            assertEquals(8, config.consumerThreads(), "from the profile file");
            assertEquals(32, config.sensorCount(), "from the environment");
            assertEquals(256, config.queueCapacity(), "from the command line");
            assertEquals(BASELINE_BATCH_SIZE, config.batchSize(), "untouched by any override");
        }

        @Test
        @DisplayName("a blank environment variable counts as unset, not as an empty value")
        void blankEnvironmentValuesAreIgnored() {
            // An unset variable in a Compose file often arrives as "", and letting that clear
            // a numeric setting would fail validation for a value nobody meant to change.
            env.put("PIPELINE_LOG_LEVEL", "   ");
            assertEquals("INFO", loader().load(Map.of()).logLevel());
        }

        @Test
        @DisplayName("surrounding whitespace is stripped from file and environment values")
        void valuesAreStripped() throws IOException {
            writeProfile("dev", "pipeline.output.dir=  ./from-file  ");
            assertEquals("./from-file", loader().load(Map.of()).outputDir());
            env.put("PIPELINE_OUTPUT_DIR", "  ./from-env  ");
            assertEquals("./from-env", loader().load(Map.of()).outputDir());
        }

        @Test
        @DisplayName("a secret set only in the environment reaches the config and nothing else")
        void secretsArriveFromTheEnvironmentOnly() {
            // The committed properties file deliberately omits these two keys, so the
            // environment is the only layer that can supply them.
            env.put("PIPELINE_HTTP_TOKEN", "0123456789abcdef-token");
            env.put("PIPELINE_JDBC_PASSWORD", "not-a-real-password");
            PipelineConfig config = loader().load(Map.of());
            assertEquals("0123456789abcdef-token", config.httpToken());
            assertEquals("not-a-real-password", config.jdbcPassword());
            assertFalse(config.toString().contains("not-a-real-password"),
                    "toString is what lands in the startup log");
        }
    }

    @Nested
    @DisplayName("profiles")
    class Profiles {

        @Test
        @DisplayName("an absent profile file is normal, not an error")
        void missingProfileFileIsFine() {
            env.put("PIPELINE_ENV", "there-is-no-such-file");
            PipelineConfig config = loader().load(Map.of());
            assertEquals("there-is-no-such-file", config.env());
            assertEquals(BASELINE_BATCH_SIZE, config.batchSize(), "the baseline still applies");
        }

        @Test
        @DisplayName("PIPELINE_ENV selects which profile file is read")
        void environmentSelectsTheProfile() throws IOException {
            writeProfile("dev", "pipeline.batch.size=11");
            writeProfile("prod", "pipeline.batch.size=22");
            env.put("PIPELINE_ENV", "prod");
            assertEquals(22, loader().load(Map.of()).batchSize());
        }

        /**
         * {@code load} cannot read layer 3 until it knows the profile, so the profile is
         * resolved from the CLI and the environment first — one step out of order and
         * {@code --pipeline.env=prod} would read the dev file and then merely relabel the run.
         */
        @Test
        @DisplayName("--pipeline.env picks the file, not just the label on the report")
        void theProfileIsChosenBeforeTheFileItSelectsIsRead() throws IOException {
            writeProfile("dev", "pipeline.batch.size=11");
            writeProfile("prod", "pipeline.batch.size=22");
            env.put("PIPELINE_ENV", "dev");
            PipelineConfig config = loader().load(Map.of(PipelineConfig.KEY_ENV, "prod"));
            assertEquals("prod", config.env());
            assertEquals(22, config.batchSize(), "the CLI profile chose the file that was read");
        }

        @Test
        @DisplayName("the profile file's own pipeline.env cannot rename the run it configures")
        void theFileCannotOverrideTheProfileThatSelectedIt() throws IOException {
            // env is re-put last on purpose: a file claiming a different name than the one
            // used to find it would make the report disagree with the file it loaded.
            writeProfile("prod", "pipeline.env=staging", "pipeline.batch.size=22");
            env.put("PIPELINE_ENV", "prod");
            PipelineConfig config = loader().load(Map.of());
            assertEquals("prod", config.env());
            assertEquals(22, config.batchSize(), "the rest of the file is still applied");
        }

        @Test
        @DisplayName("with no profile named anywhere, the baseline's env is used")
        void baselineSuppliesTheProfileName() throws IOException {
            writeProfile("dev", "pipeline.batch.size=33");
            assertEquals(33, loader().load(Map.of()).batchSize(),
                    "application.properties says env=dev, so the dev file is the one read");
        }

        @Test
        @DisplayName("a blank profile name falls through to the next source")
        void blankProfileNamesFallThrough() throws IOException {
            writeProfile("dev", "pipeline.batch.size=44");
            env.put("PIPELINE_ENV", "  ");
            assertEquals(44, loader().load(Map.of()).batchSize());
            assertEquals("dev", loader().load(Map.of()).env());
        }
    }

    @Nested
    @DisplayName("unknown keys")
    class UnknownKeys {

        @Test
        @DisplayName("an unknown CLI key is rejected, naming the command line")
        void unknownCliKeyIsRejected() {
            ConfigurationException thrown = assertThrows(ConfigurationException.class,
                    () -> loader().load(Map.of("pipeline.consumer.thread", "8")));
            assertTrue(thrown.getMessage().contains("pipeline.consumer.thread"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("the command line"), thrown.getMessage());
        }

        /**
         * The gap this test closes: a typo in a profile file used to be silently ignored, so
         * {@code pipeline.consumer.thread=8} in {@code application-prod.properties} ran the
         * default four threads and the benchmark number meant nothing.
         */
        @Test
        @DisplayName("an unknown key in a profile file is rejected, naming the file")
        void unknownProfileKeyIsRejected() throws IOException {
            writeProfile("dev", "pipeline.consumer.thread=8");
            ConfigurationException thrown =
                    assertThrows(ConfigurationException.class, () -> loader().load(Map.of()));
            assertTrue(thrown.getMessage().contains("pipeline.consumer.thread"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("application-dev.properties"),
                    "the message must say which file to edit: " + thrown.getMessage());
        }

        @Test
        @DisplayName("a commented-out or blank line in a profile file is not a key")
        void commentsAndBlanksAreNotKeys() throws IOException {
            writeProfile("dev", "# pipeline.nonsense=1", "", "   ", "pipeline.batch.size=64");
            assertEquals(64, loader().load(Map.of()).batchSize());
        }

        @Test
        @DisplayName("a key present but blank in a profile file is dropped, not rejected")
        void blankFileValuesAreDropped() throws IOException {
            // jdbc.url ships blank in the baseline for exactly this reason: "declared but
            // empty" has to mean "not configured", or the file could not document a setting
            // without also enabling it.
            writeProfile("dev", "pipeline.jdbc.url=");
            assertTrue(loader().load(Map.of()).jdbcUrl().isEmpty());
        }

        @Test
        @DisplayName("an unrecognised PIPELINE_ variable is invisible rather than fatal")
        void unknownEnvironmentVariablesAreIgnored() {
            // The process environment belongs to the whole machine, so rejecting names this
            // application does not own would break a run for something else's variable.
            env.put("PIPELINE_CONSUMER_THREAD", "8");
            env.put("PIPELINE_SOMETHING_ELSE_ENTIRELY", "x");
            assertEquals(4, loader().load(Map.of()).consumerThreads(), "the baseline value stands");
        }
    }

    @Nested
    @DisplayName("value parsing")
    class ValueParsing {

        @Test
        @DisplayName("a non-numeric int names the key, the expected type and the bad value")
        void malformedIntIsReported() {
            env.put("PIPELINE_BATCH_SIZE", "many");
            ConfigurationException thrown =
                    assertThrows(ConfigurationException.class, () -> loader().load(Map.of()));
            assertTrue(thrown.getMessage().contains(PipelineConfig.KEY_BATCH_SIZE), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("must be an integer"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("many"), thrown.getMessage());
        }

        @Test
        @DisplayName("a non-numeric long is reported as a whole number")
        void malformedLongIsReported() {
            env.put("PIPELINE_EVENT_COUNT", "1_000_000");
            ConfigurationException thrown =
                    assertThrows(ConfigurationException.class, () -> loader().load(Map.of()));
            assertTrue(thrown.getMessage().contains("must be a whole number"), thrown.getMessage());
        }

        @Test
        @DisplayName("a non-numeric double is reported as a decimal number")
        void malformedDoubleIsReported() {
            env.put("PIPELINE_FILTER_THRESHOLD", "50,0");
            ConfigurationException thrown =
                    assertThrows(ConfigurationException.class, () -> loader().load(Map.of()));
            assertTrue(thrown.getMessage().contains("must be a decimal number"), thrown.getMessage());
            // A comma decimal separator is the commonest form of this mistake in Europe, and
            // the message has to show the value so the reader sees the comma.
            assertTrue(thrown.getMessage().contains("50,0"), thrown.getMessage());
        }

        /**
         * {@code Boolean.parseBoolean} answers {@code false} to every typo, so a misspelled
         * flag would silently disable a feature and read as a deliberate choice in the log.
         */
        @ParameterizedTest
        @DisplayName("a boolean that is not true or false is rejected rather than read as false")
        @ValueSource(strings = {"ture", "yes", "1", "on", "enabled", "TRUE!"})
        void malformedBooleanIsRejected(String value) {
            env.put("PIPELINE_DASHBOARD_ENABLED", value);
            ConfigurationException thrown =
                    assertThrows(ConfigurationException.class, () -> loader().load(Map.of()));
            assertTrue(thrown.getMessage().contains("must be 'true' or 'false'"), thrown.getMessage());
        }

        @ParameterizedTest
        @DisplayName("booleans are case-insensitive, so TRUE and False both work")
        @CsvSource({"true, true", "TRUE, true", "True, true", "false, false", "FALSE, false", "False, false"})
        void booleansAreCaseInsensitive(String value, boolean expected) {
            env.put("PIPELINE_DASHBOARD_ENABLED", value);
            assertEquals(expected, loader().load(Map.of()).dashboardEnabled());
        }

        @Test
        @DisplayName("a negative number parses and then fails validation, with a range message")
        void outOfRangeValuesFailValidationNotParsing() {
            // Parsing and validating are separate jobs: -1 is a perfectly good integer, and
            // the message an operator needs says "must be positive", not "must be an integer".
            env.put("PIPELINE_QUEUE_CAPACITY", "-1");
            ConfigurationException thrown =
                    assertThrows(ConfigurationException.class, () -> loader().load(Map.of()));
            assertFalse(thrown.getMessage().contains("must be an integer"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains(PipelineConfig.KEY_QUEUE_CAPACITY), thrown.getMessage());
        }

        @Test
        @DisplayName("a well-formed value from every layer parses into the right type")
        void everyTypeRoundTrips() throws IOException {
            writeProfile("dev",
                    "pipeline.event.count=250",
                    "pipeline.batch.size=32",
                    "pipeline.filter.threshold=12.5",
                    "pipeline.dashboard.enabled=false",
                    "pipeline.log.level=FINE",
                    "pipeline.output.dir=./target/config-test-output");
            PipelineConfig config = loader().load(Map.of());
            assertEquals(250L, config.eventCount());
            assertEquals(32, config.batchSize());
            assertEquals(12.5, config.filterThreshold(), 0.0);
            assertFalse(config.dashboardEnabled());
            assertEquals("FINE", config.logLevel());
            assertEquals("./target/config-test-output", config.outputDir());
        }
    }
}
