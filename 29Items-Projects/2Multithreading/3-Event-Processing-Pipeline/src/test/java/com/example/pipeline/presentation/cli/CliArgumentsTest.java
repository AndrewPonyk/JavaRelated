package com.example.pipeline.presentation.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.pipeline.infrastructure.config.ConfigLoader;
import com.example.pipeline.infrastructure.config.ConfigurationException;
import com.example.pipeline.infrastructure.config.PipelineConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The command-line grammar, including the parts that exist to reject input.
 *
 * <p>The rejections are the interesting half. A silently ignored
 * {@code --pipeline.consumer.thread=8} (missing "s") produces a run that used the default
 * and a benchmark number that means nothing, so every test below that expects a
 * {@link ConfigurationException} is guarding against a wrong answer rather than a crash.
 */
@DisplayName("CliArguments")
class CliArgumentsTest {

    private static final String[] NONE = new String[0];

    @Nested
    @DisplayName("accepted input")
    class Accepted {

        @Test
        @DisplayName("no arguments means no overrides and no help")
        void emptyArgsYieldNothing() {
            CliArguments parsed = CliArguments.parse(NONE);
            assertTrue(parsed.isEmpty());
            assertTrue(parsed.overrides().isEmpty());
            assertFalse(parsed.helpRequested());
        }

        @Test
        @DisplayName("a --key=value flag becomes an override under the property name verbatim")
        void keyValueBecomesOverride() {
            CliArguments parsed = CliArguments.parse(new String[] {"--pipeline.batch.size=256"});
            assertEquals(Map.of(PipelineConfig.KEY_BATCH_SIZE, "256"), parsed.overrides());
            assertFalse(parsed.isEmpty());
        }

        @Test
        @DisplayName("several flags are all kept, in the order given")
        void multipleFlagsArePreservedInOrder() {
            CliArguments parsed = CliArguments.parse(new String[] {
                "--pipeline.event.count=1000",
                "--pipeline.batch.size=64",
                "--pipeline.consumer.threads=2",
            });
            assertEquals(List.of(PipelineConfig.KEY_EVENT_COUNT, PipelineConfig.KEY_BATCH_SIZE,
                            PipelineConfig.KEY_CONSUMER_THREADS),
                    List.copyOf(parsed.overrides().keySet()));
        }

        @Test
        @DisplayName("surrounding whitespace is stripped from the flag, the key and the value")
        void whitespaceIsStripped() {
            CliArguments parsed = CliArguments.parse(new String[] {"  --pipeline.batch.size = 128  "});
            assertEquals("128", parsed.overrides().get(PipelineConfig.KEY_BATCH_SIZE));
        }

        @Test
        @DisplayName("a blank argument is skipped, not treated as malformed")
        void blankArgumentsAreSkipped() {
            // A shell expanding an empty variable produces exactly this; failing on it would
            // make "$EXTRA_FLAGS" unusable in a launch script.
            CliArguments parsed = CliArguments.parse(new String[] {"", "   ", "--pipeline.batch.size=8", "\t"});
            assertEquals(1, parsed.overrides().size());
        }

        @Test
        @DisplayName("an empty value is allowed - it is how a setting is cleared")
        void emptyValueIsAllowed() {
            // --pipeline.output.dir= is the documented way to say "no CSV report".
            CliArguments parsed = CliArguments.parse(new String[] {"--pipeline.output.dir="});
            assertEquals("", parsed.overrides().get(PipelineConfig.KEY_OUTPUT_DIR));
        }

        @Test
        @DisplayName("a value containing '=' keeps everything after the first one")
        void onlyTheFirstEqualsSplits() {
            // A JDBC URL with query parameters is the realistic case.
            CliArguments parsed = CliArguments.parse(
                    new String[] {"--pipeline.jdbc.url=jdbc:postgresql://h/db?a=1&b=2"});
            assertEquals("jdbc:postgresql://h/db?a=1&b=2", parsed.overrides().get(PipelineConfig.KEY_JDBC_URL));
        }

        @Test
        @DisplayName("the overrides map is immutable")
        void overridesAreImmutable() {
            Map<String, String> overrides = CliArguments.parse(
                    new String[] {"--pipeline.batch.size=8"}).overrides();
            assertThrows(UnsupportedOperationException.class, () -> overrides.put("x", "y"));
        }

        @Test
        @DisplayName("toString names the overrides, for the startup log")
        void toStringIsInformative() {
            String text = CliArguments.parse(new String[] {"--pipeline.batch.size=8", "--help"}).toString();
            assertTrue(text.contains(PipelineConfig.KEY_BATCH_SIZE), text);
            assertTrue(text.contains("+help"), text);
        }
    }

    @Nested
    @DisplayName("help")
    class Help {

        @ParameterizedTest
        @DisplayName("--help and -h both request usage")
        @ValueSource(strings = {"--help", "-h"})
        void helpFlagsAreRecognised(String flag) {
            assertTrue(CliArguments.parse(new String[] {flag}).helpRequested());
        }

        @Test
        @DisplayName("help combines with overrides without failing")
        void helpAlongsideOverrides() {
            CliArguments parsed = CliArguments.parse(new String[] {"--pipeline.batch.size=8", "--help"});
            assertTrue(parsed.helpRequested());
            assertEquals(1, parsed.overrides().size());
        }

        @Test
        @DisplayName("usage lists every known setting")
        void usageListsEveryKnownKey() {
            String usage = CliArguments.usage();
            for (String key : ConfigLoader.knownKeys()) {
                assertTrue(usage.contains("--" + key + "=<value>"), "usage omits " + key);
            }
        }

        @Test
        @DisplayName("usage documents the exit-code contract and the env-var mapping")
        void usageDocumentsExitCodesAndEnvVars() {
            String usage = CliArguments.usage();
            assertTrue(usage.contains("0   run completed"), usage);
            assertTrue(usage.contains("1   run failed"), usage);
            assertTrue(usage.contains("2   invalid configuration"), usage);
            assertTrue(usage.contains("130 interrupted"), usage);
            assertTrue(usage.contains(ConfigLoader.envVarFor(PipelineConfig.KEY_BATCH_SIZE)), usage);
        }

        @Test
        @DisplayName("usage is ASCII only, so it renders in the default Windows code page")
        void usageIsAscii() {
            String usage = CliArguments.usage();
            for (int i = 0; i < usage.length(); i++) {
                char c = usage.charAt(i);
                assertTrue(c < 0x80, "non-ASCII character U+" + Integer.toHexString(c) + " in usage text");
            }
        }
    }

    @Nested
    @DisplayName("rejected input")
    class Rejected {

        @Test
        @DisplayName("a null argument array is rejected")
        void nullArrayIsRejected() {
            assertThrows(NullPointerException.class, () -> CliArguments.parse(null));
        }

        @Test
        @DisplayName("a misspelled key is rejected rather than ignored")
        void unknownKeyIsRejected() {
            ConfigurationException thrown = assertThrows(ConfigurationException.class,
                    () -> CliArguments.parse(new String[] {"--pipeline.consumer.thread=8"}));
            assertTrue(thrown.getMessage().contains("pipeline.consumer.thread"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("--help"), "the message should point at the key list");
        }

        @ParameterizedTest
        @DisplayName("an argument that is not a --key=value flag is rejected")
        @ValueSource(strings = {"pipeline.batch.size=8", "-x", "8", "run"})
        void nonFlagArgumentsAreRejected(String arg) {
            assertThrows(ConfigurationException.class, () -> CliArguments.parse(new String[] {arg}));
        }

        @ParameterizedTest
        @DisplayName("a flag without a well-formed key=value body is rejected")
        @ValueSource(strings = {"--", "--=8", "--pipeline.batch.size", "--nonsense"})
        void malformedFlagsAreRejected(String arg) {
            assertThrows(ConfigurationException.class, () -> CliArguments.parse(new String[] {arg}));
        }

        @Test
        @DisplayName("the same key twice is rejected, rather than last-one-wins")
        void repeatedKeyIsRejected() {
            ConfigurationException thrown = assertThrows(ConfigurationException.class,
                    () -> CliArguments.parse(new String[] {"--pipeline.batch.size=8", "--pipeline.batch.size=16"}));
            assertTrue(thrown.getMessage().contains("more than once"), thrown.getMessage());
        }

        @Test
        @DisplayName("a repeat is caught even when the two values agree")
        void repeatedKeyWithSameValueIsStillRejected() {
            assertThrows(ConfigurationException.class,
                    () -> CliArguments.parse(new String[] {"--pipeline.batch.size=8", "--pipeline.batch.size=8"}));
        }

        @Test
        @DisplayName("parsing stops at the first bad flag, so nothing is half-applied")
        void parsingIsAllOrNothing() {
            assertThrows(ConfigurationException.class, () -> CliArguments.parse(new String[] {
                "--pipeline.batch.size=8",
                "--not.a.key=1",
                "--pipeline.event.count=10",
            }));
        }
    }
}
