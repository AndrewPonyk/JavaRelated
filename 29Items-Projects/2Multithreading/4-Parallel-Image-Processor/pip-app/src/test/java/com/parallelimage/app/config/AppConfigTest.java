package com.parallelimage.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.fork.ForkJoinConfig;
import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.pipeline.ImageOperation;
import com.parallelimage.persistence.jdbc.Database;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link AppConfig} tests.
 *
 * <p>This class is the one place in the application that reads the outside world, so what is being tested
 * is mostly <b>precedence</b> and <b>what happens to a bad value</b>. The second matters more than it
 * looks: a properties file is edited by hand, and the two failure modes worth designing against are a
 * value that is silently ignored (the operator believes it took effect) and a value that stops the
 * application from starting (the operator's tool for fixing configuration is the UI, and the UI needs the
 * application to run). The policy throughout is therefore <i>warn and fall back</i>, and most of the
 * tests below pin one instance of it.
 *
 * <h2>Which layers these tests can reach</h2>
 * Three of the four. {@link AppConfig#load(Path)} takes the external file as a parameter — which is the
 * reason that overload exists — and system properties are settable in-process and restored after each
 * test. <b>Environment variables are not settable in-process at all</b>, which is precisely the
 * untestability the class exists to contain: every other class takes its settings as constructor
 * arguments.
 *
 * <p>The env layer is still covered indirectly, and exactly where it can go wrong. Its whole content is
 * the {@code ENV_ALIASES} table, and {@code applySystemProperties} iterates that same table — so
 * {@link Layers#everySettingIsReachableFromASystemProperty} fails for any documented setting missing from
 * it, which is the one mistake that would make a {@code PIP_*} variable silently do nothing.
 *
 * <p>The packaged classpath resource is a no-op in this build: {@code pip-app} has no
 * {@code /config/application.properties}. {@link Layers#nothingConfiguredMeansCompiledInDefaults} is
 * where that assumption is recorded, and it is the test that will fail if one is ever added.
 */
class AppConfigTest {

    @TempDir
    private Path dir;

    /** An external file that does not exist, for testing the layers below it. */
    private Path missing;

    /** System properties this test set, cleared afterwards so they cannot leak into another class. */
    private final List<String> propertiesSet = new ArrayList<>();

    @BeforeEach
    void locateMissingFile() {
        missing = dir.resolve("absent.properties");
    }

    @AfterEach
    void clearSystemProperties() {
        propertiesSet.forEach(System::clearProperty);
        propertiesSet.clear();
    }

    /** Sets a system property for the duration of one test. */
    private void property(String name, String value) {
        propertiesSet.add(name);
        System.setProperty(name, value);
    }

    /** The file {@link #configFrom} last wrote, for the tests that assert on its path. */
    private Path externalFile;

    /** Writes an external properties file and loads it. */
    private AppConfig configFrom(String... lines) throws IOException {
        // Files.writeString is UTF-8, matching the InputStreamReader in loadExternal. Writing it any
        // other way would make the non-ASCII test below assert the test's own encoding bug.
        externalFile = Files.writeString(dir.resolve("application.properties"), String.join("\n", lines));
        return AppConfig.load(externalFile);
    }

    /**
     * A path as it must be written into a properties file.
     *
     * <p>{@code \} is an escape character there, so a Windows path written literally loses every
     * separator — {@code C:\photos\pip.db} reads back as {@code C:photospip.db}, and a component
     * beginning with {@code t} or {@code n} becomes a control character. Forward slashes avoid the whole
     * problem and {@link Path} accepts them on Windows, which is why {@code config/application.properties}
     * documents that form.
     */
    private static String asPropertyValue(Path path) {
        return path.toString().replace('\\', '/');
    }

    @Nested
    @DisplayName("layers")
    class Layers {

        @Test
        @DisplayName("a missing external file is not an error")
        void missingFileIsFine() {
            assertFalse(Files.exists(missing));
            AppConfig config = AppConfig.load(missing);
            assertEquals(ForkJoinConfig.defaultParallelism(), config.parallelism());
        }

        @Test
        @DisplayName("the no-argument load() reads the documented location and tolerates its absence")
        void defaultLocation() {
            // Relative on purpose: 'config/' sits next to the launcher script in the distribution zip,
            // so an operator who unzips two copies gets two independent configurations.
            assertEquals(Path.of("config", "application.properties"), AppConfig.EXTERNAL_FILE);
            assertFalse(AppConfig.EXTERNAL_FILE.isAbsolute());
            AppConfig.load().describe();
        }

        @Test
        @DisplayName("nothing configured means every value is the compiled-in one")
        void nothingConfiguredMeansCompiledInDefaults() {
            AppConfig config = AppConfig.load(missing);
            assertEquals(ProcessingOptions.defaults(), config.defaultOptions(),
                    "with no file and no system properties, defaultOptions must be ProcessingOptions'"
                    + " own defaults -- if this fails, pip-app has gained a packaged"
                    + " /config/application.properties and these tests now observe it");
            assertFalse(config.apiEnabled(), "the control API must be off unless asked for");
            assertEquals(8137, config.apiPort());
            assertEquals(Optional.empty(), config.apiToken());
        }

        @Test
        @DisplayName("the external file overrides the compiled-in default")
        void fileOverridesDefaults() throws IOException {
            assertEquals("jpg", configFrom("output.format=jpg").defaultOptions().outputFormat());
        }

        @Test
        @DisplayName("a system property overrides the external file")
        void systemPropertyOverridesFile() throws IOException {
            property("pip.output.format", "webp");
            assertEquals("webp", configFrom("output.format=jpg").defaultOptions().outputFormat(),
                    "-D is the most explicit and most local layer, so it wins");
        }

        @ParameterizedTest
        @DisplayName("every documented setting is reachable from a system property")
        @ValueSource(strings = {
            "db.path", "db.enabled",
            "batches.parallelism", "batches.tileThresholdPixels", "batches.batchThresholdJobs",
            "batches.maxPixelsPerImage",
            "output.format", "output.quality", "output.stripMetadata", "output.overwriteExisting",
            "pipeline.default",
            "api.enabled", "api.port", "api.token",
            "retention.days",
        })
        void everySettingIsReachableFromASystemProperty(String key) {
            // Both the -D layer and the environment layer iterate the same ENV_ALIASES table, so a
            // setting missing from it is unreachable from either -- a PIP_* variable that silently does
            // nothing. This is the closest a unit test can get to covering the environment layer.
            property("pip." + key, "1");
            String described = AppConfig.load(missing).describe();
            assertTrue(described.contains("(-Dpip." + key + ")"),
                    key + " is not in ENV_ALIASES, so neither -Dpip." + key + " nor its PIP_ variable"
                    + " has any effect: " + described);
        }

        @Test
        @DisplayName("a blank system property is ignored rather than blanking the setting")
        void blankSystemPropertyIsIgnored() throws IOException {
            property("pip.output.format", "   ");
            assertEquals("jpg", configFrom("output.format=jpg").defaultOptions().outputFormat());
        }

        @Test
        @DisplayName("values are trimmed, so a trailing space cannot make a number unparseable")
        void valuesAreTrimmed() throws IOException {
            // Properties.load keeps trailing whitespace; merge() trims it. Leading whitespace is already
            // Properties' own business.
            assertEquals(0.5f, configFrom("output.quality=0.5   ").defaultOptions().quality());
        }

        @Test
        @DisplayName("the file is read as UTF-8, not as ISO-8859-1")
        void fileIsUtf8() throws IOException {
            // Properties.load(InputStream) is ISO-8859-1: it would decode the two UTF-8 bytes of e-acute
            // as two separate characters. The symptom of getting this wrong is a path -- or a watermark --
            // that is subtly wrong in a way nobody notices until it is in 4 000 files.
            //
            // A literal character is safe here only because the parent pom sets
            // project.build.sourceEncoding=UTF-8; without it javac would read this file in the platform
            // encoding and the assertion would compare two different manglings.
            AppConfig config = configFrom("db.path=Olé.db");
            assertEquals(Optional.of(Path.of("Olé.db")), config.databasePath());
        }

        @Test
        @DisplayName("a file that exists but cannot be read is fatal, unlike one that is absent")
        void unreadableFileIsFatal() {
            // A directory where a file belongs: readable by the isReadable check, not openable as a
            // stream. Refusing to start is right -- the operator wrote a configuration we can see and
            // cannot honour, and starting with defaults would silently discard it.
            assertThrows(UncheckedIOException.class, () -> AppConfig.load(dir));
        }
    }

    @Nested
    @DisplayName("booleans")
    class Booleans {

        @ParameterizedTest
        @DisplayName("the four spellings of true are accepted, in any case")
        @ValueSource(strings = {"true", "TRUE", "True", "yes", "YES", "on", "1"})
        void trueSpellings(String value) throws IOException {
            assertTrue(configFrom("api.enabled=" + value).apiEnabled(), value);
        }

        @ParameterizedTest
        @DisplayName("the four spellings of false are accepted, in any case")
        @ValueSource(strings = {"false", "FALSE", "False", "no", "NO", "off", "0"})
        void falseSpellings(String value) throws IOException {
            assertEquals(Optional.empty(), configFrom("db.enabled=" + value).databasePath(), value);
        }

        @ParameterizedTest
        @DisplayName("anything else keeps the default instead of becoming false")
        @ValueSource(strings = {"ture", "enabled", "y", "-1", "2", "null"})
        void unrecognisedKeepsTheDefault(String value) throws IOException {
            // Boolean.parseBoolean maps every one of these to false. For db.enabled that silently
            // disables history and reads as a bug in the repository layer; for output.stripMetadata it
            // silently starts publishing GPS coordinates. Both are worse than ignoring the typo, which
            // at least leaves the documented default in place -- and logs a warning.
            assertTrue(configFrom("output.stripMetadata=" + value).defaultOptions().stripMetadata(), value);
        }

        @Test
        @DisplayName("a wider vocabulary than the HTTP API accepts, deliberately")
        void widerThanTheApi() throws IOException {
            // JobRequestValidator refuses "yes" outright. The difference is the author: a properties file
            // is written by a person, for whom yes/on/1 are ordinary ini spellings, while a JSON body is
            // produced by a program against a wire format that has only true and false -- there, "yes" is
            // more likely a client serialising the wrong type than an intent. Both layers agree on the
            // part that matters: neither ever turns an unrecognised string into false.
            assertTrue(configFrom("output.overwriteExisting=yes").defaultOptions().overwriteExisting());
        }

        @Test
        @DisplayName("a boolean can be turned off as well as on")
        void falseOverridesATrueDefault() throws IOException {
            assertFalse(configFrom("output.stripMetadata=false").defaultOptions().stripMetadata(),
                    "strip-metadata defaults on, so this is the direction that must work");
        }
    }

    @Nested
    @DisplayName("numbers")
    class Numbers {

        @Test
        @DisplayName("a configured value is read")
        void configuredValues() throws IOException {
            ProcessingOptions options = configFrom(
                    "output.quality=0.25",
                    "batches.tileThresholdPixels=40000",
                    "batches.batchThresholdJobs=16",
                    "batches.maxPixelsPerImage=9999999999").defaultOptions();

            assertEquals(0.25f, options.quality());
            assertEquals(40_000L, options.tileThresholdPixels());
            assertEquals(16, options.batchThresholdJobs());
            assertEquals(9_999_999_999L, options.maxPixelsPerImage(),
                    "a pixel budget above Integer.MAX_VALUE must survive, so this cannot be read as an int");
        }

        @Test
        @DisplayName("an unparseable value warns and falls back rather than stopping startup")
        void unparseableFallsBack() throws IOException {
            // The literal example from integer()'s javadoc: '#' is not a comment character mid-line in a
            // properties file, so the value is the whole string "8 # threads".
            assertEquals(ForkJoinConfig.defaultParallelism(),
                    configFrom("batches.parallelism=8 # threads").parallelism());
            assertEquals(ProcessingOptions.DEFAULT_TILE_THRESHOLD_PIXELS,
                    configFrom("batches.tileThresholdPixels=lots").defaultOptions().tileThresholdPixels());
            assertEquals(0.9f, configFrom("output.quality=high").defaultOptions().quality());
        }

        @ParameterizedTest
        @DisplayName("a quality outside 0.0..1.0 falls back instead of throwing from the core")
        @ValueSource(strings = {"1.5", "-0.5", "85", "NaN", "Infinity", "-Infinity"})
        void qualityOutOfRangeFallsBack(String value) throws IOException {
            // Unchecked, this reaches ProcessingOptions, whose constructor rejects it with a plain
            // IllegalArgumentException -- so one wrong character in a properties file becomes a startup
            // failure. NaN and Infinity are in the list because Double.parseDouble accepts both, and a
            // range test written "below min or above max" is false for NaN and would let it through.
            assertEquals(0.9f, configFrom("output.quality=" + value).defaultOptions().quality(), value);
        }

        @ParameterizedTest
        @DisplayName("a threshold of zero or less falls back, for the same reason")
        @ValueSource(strings = {"0", "-1", "-65536"})
        void thresholdsMustBePositive(String value) throws IOException {
            ProcessingOptions options = configFrom(
                    "batches.tileThresholdPixels=" + value,
                    "batches.batchThresholdJobs=" + value,
                    "batches.maxPixelsPerImage=" + value).defaultOptions();

            // Zero is not treated as "unset" here, unlike parallelism: a tile threshold of zero splits
            // until every leaf is a single pixel, and a pixel budget of zero rejects every image as a
            // decode bomb. Both look like plausible values and neither is survivable.
            assertEquals(ProcessingOptions.DEFAULT_TILE_THRESHOLD_PIXELS, options.tileThresholdPixels());
            assertEquals(ProcessingOptions.DEFAULT_BATCH_THRESHOLD_JOBS, options.batchThresholdJobs());
            assertEquals(ProcessingOptions.DEFAULT_MAX_PIXELS, options.maxPixelsPerImage());
        }

        @Test
        @DisplayName("quality accepts both ends of the range")
        void qualityBoundsAreInclusive() throws IOException {
            assertEquals(0.0f, configFrom("output.quality=0").defaultOptions().quality());
            assertEquals(1.0f, configFrom("output.quality=1").defaultOptions().quality());
        }
    }

    @Nested
    @DisplayName("parallelism")
    class Parallelism {

        @Test
        @DisplayName("a positive value is honoured")
        void positiveValue() throws IOException {
            assertEquals(6, configFrom("batches.parallelism=6").parallelism());
        }

        @ParameterizedTest
        @DisplayName("zero or negative means the engine's default, rather than being refused")
        @ValueSource(strings = {"0", "-1", "-8"})
        void nonPositiveMeansDefault(String value) throws IOException {
            // PIP_PARALLELISM=0 from a launcher script whose arithmetic produced nothing should start the
            // application. This is the one numeric setting where zero is 'unset' rather than 'wrong'.
            assertEquals(ForkJoinConfig.defaultParallelism(),
                    configFrom("batches.parallelism=" + value).parallelism(), value);
        }

        @Test
        @DisplayName("the fallback is at least one worker")
        void fallbackIsUsable() {
            assertTrue(AppConfig.load(missing).parallelism() >= 1);
        }
    }

    @Nested
    @DisplayName("the default pipeline")
    class DefaultPipeline {

        @Test
        @DisplayName("a configured spec is parsed by PipelineFormat")
        void parsed() throws IOException {
            assertEquals(List.of(new ImageOperation.Grayscale(), new ImageOperation.Resize(100, 100, true)),
                    configFrom("pipeline.default=grayscale>resize:100x100:fit").defaultOptions().operations());
        }

        @Test
        @DisplayName("no pipeline configured is an empty pipeline, not an error")
        void absent() {
            assertEquals(List.of(), AppConfig.load(missing).defaultOptions().operations());
        }

        @Test
        @DisplayName("an unparseable spec is logged and dropped, never fatal")
        void unparseableIsNotFatal() throws IOException {
            // The alternative -- refusing to start -- leaves the operator with one typo in a file and no
            // UI in which to fix it. The warning is the compensating control.
            assertEquals(List.of(),
                    configFrom("pipeline.default=grayscale>bogus:9").defaultOptions().operations());
        }
    }

    @Nested
    @DisplayName("the format")
    class Format {

        @Test
        @DisplayName("jpeg is normalised to jpg by the core model")
        void normalised() throws IOException {
            assertEquals("jpg", configFrom("output.format=JPEG").defaultOptions().outputFormat());
        }

        @Test
        @DisplayName("a blank format is the same as an absent one")
        void blankIsAbsent() throws IOException {
            // ProcessingOptions rejects a blank outputFormat outright, so string()'s blank-is-absent rule
            // is what keeps 'output.format=' in a file from being a startup failure.
            assertEquals("png", configFrom("output.format=").defaultOptions().outputFormat());
        }
    }

    @Nested
    @DisplayName("the database path")
    class DatabasePath {

        @Test
        @DisplayName("absent means the default location under the user's home")
        void defaultLocation() {
            assertEquals(Optional.of(Database.defaultPath()), AppConfig.load(missing).databasePath());
        }

        @Test
        @DisplayName("a configured path is used as given")
        void configured() throws IOException {
            Path db = dir.resolve("history.db");
            assertEquals(Optional.of(db),
                    configFrom("db.path=" + asPropertyValue(db)).databasePath(),
                    "an absolute path must survive the round trip through the file unchanged");
        }

        @Test
        @DisplayName("a leading ~ is expanded, because the shell does not do it for a file")
        void tildeIsExpanded() throws IOException {
            // Path.of("~/pip.db") creates a directory literally named '~' in the working directory:
            // confusing to find and easy to leave behind.
            Path resolved = configFrom("db.path=~/custom.db").databasePath().orElseThrow();
            assertEquals(Path.of(System.getProperty("user.home"), "custom.db"), resolved);
            assertFalse(resolved.toString().contains("~"), resolved.toString());
        }

        @Test
        @DisplayName("a ~ anywhere but the front is an ordinary character")
        void tildeElsewhereIsLiteral() throws IOException {
            // Windows short names contain one, e.g. PROGRA~1, and a path is not the shell's to rewrite.
            assertEquals(Optional.of(Path.of("PROGRA~1", "pip.db")),
                    configFrom("db.path=PROGRA~1/pip.db").databasePath());
        }

        @Test
        @DisplayName("history switched off is a supported configuration, not a degraded one")
        void disabled() throws IOException {
            // How --no-history works, and how the application runs from a read-only share.
            assertEquals(Optional.empty(),
                    configFrom("db.enabled=false",
                            "db.path=" + asPropertyValue(dir.resolve("ignored.db"))).databasePath(),
                    "db.enabled=false must win over a configured path");
        }

        @Test
        @DisplayName("a blank path falls back to the default rather than to the working directory")
        void blankPath() throws IOException {
            assertEquals(Optional.of(Database.defaultPath()), configFrom("db.path=").databasePath());
        }
    }

    @Nested
    @DisplayName("the control API")
    class ControlApi {

        @Test
        @DisplayName("off by default, since it opens a port")
        void offByDefault() {
            assertFalse(AppConfig.load(missing).apiEnabled());
        }

        @Test
        @DisplayName("the port is configurable")
        void port() throws IOException {
            assertEquals(9100, configFrom("api.port=9100").apiPort());
        }

        @Test
        @DisplayName("an impossible port is passed through, so the bind fails loudly")
        void portIsNotRangeChecked() throws IOException {
            // Deliberately unlike the settings in defaultOptions(). Quietly listening on 8137 after being
            // told 99999 leaves a client unable to connect to something that is nevertheless running,
            // which is harder to diagnose than a refusal to start.
            assertEquals(99_999, configFrom("api.port=99999").apiPort());
        }

        @Test
        @DisplayName("a token is optional, and blank counts as absent")
        void token() throws IOException {
            assertEquals(Optional.empty(), configFrom("api.token=").apiToken());
            assertEquals(Optional.empty(), configFrom("api.token=   ").apiToken());
            assertEquals(Optional.of("s3cr3t"), configFrom("api.token=s3cr3t").apiToken());
        }
    }

    @Nested
    @DisplayName("retention")
    class Retention {

        @Test
        @DisplayName("unset means keep history forever")
        void unsetMeansForever() {
            assertEquals(0, AppConfig.load(missing).retentionDays());
        }

        @Test
        @DisplayName("a positive value is honoured")
        void positiveValue() throws IOException {
            assertEquals(30, configFrom("retention.days=30").retentionDays());
        }

        @ParameterizedTest
        @DisplayName("zero, negative, or unparseable all floor to zero, not to a fallback default")
        @ValueSource(strings = {"0", "-1", "-365", "lots"})
        void nonPositiveOrUnparseableFloorsToZero(String value) throws IOException {
            // Unlike parallelism(), there is no separate "computed default" to fall back to here: zero
            // is itself the documented off state, so a bad value collapses to the same off state rather
            // than to some other retention window nobody configured.
            assertEquals(0, configFrom("retention.days=" + value).retentionDays(), value);
        }
    }

    @Nested
    @DisplayName("describe()")
    class Describe {

        @Test
        @DisplayName("each setting is listed with its value and where it came from")
        void valueAndOrigin() throws IOException {
            property("pip.api.port", "9100");

            String described = configFrom("output.format=jpg").describe();
            assertTrue(described.contains("output.format = jpg"), described);
            assertTrue(described.contains(externalFile.toString()),
                    "the file's own path is the origin, so 'which file was that?' needs no investigation: "
                    + described);
            assertTrue(described.contains("api.port = 9100"), described);
            assertTrue(described.contains("(-Dpip.api.port)"), described);
        }

        @Test
        @DisplayName("the token is redacted, because a startup dump ends up in support transcripts")
        void tokenIsRedacted() throws IOException {
            String described = configFrom("api.token=s3cr3t-do-not-print").describe();
            assertTrue(described.contains("api.token = ***"), described);
            assertFalse(described.contains("s3cr3t"),
                    "the only secret this application has must not appear in its own diagnostics");
        }

        @Test
        @DisplayName("an unset setting is omitted rather than shown as empty")
        void unsetSettingsAreOmitted() throws IOException {
            String described = configFrom("output.format=jpg").describe();
            assertTrue(described.contains("output.format"), described);
            assertFalse(described.contains("api.token"),
                    "listing every key with a blank value buries the four that were actually set");
        }

        @Test
        @DisplayName("the order is stable, so two dumps can be diffed")
        void orderIsStable() throws IOException {
            // ENV_ALIASES is a LinkedHashMap for exactly this: HashMap order changes when a key is added,
            // which makes every dump differ from the last one for no reason.
            String described = configFrom("api.port=9100", "db.enabled=true", "output.format=jpg").describe();
            assertTrue(described.indexOf("db.enabled") < described.indexOf("output.format"), described);
            assertTrue(described.indexOf("output.format") < described.indexOf("api.port"), described);
        }

        @Test
        @DisplayName("nothing configured still produces a heading rather than an empty string")
        void emptyConfiguration() {
            assertEquals("configuration:", AppConfig.load(missing).describe());
        }

        @Test
        @DisplayName("toString is a summary, not the whole dump")
        void toStringIsShort() throws IOException {
            String text = configFrom("output.format=jpg", "api.port=9100").toString();
            assertEquals("AppConfig[2 settings]", text);
            assertFalse(text.contains("jpg"), "toString appears in log lines; describe() is the dump");
        }
    }
}
