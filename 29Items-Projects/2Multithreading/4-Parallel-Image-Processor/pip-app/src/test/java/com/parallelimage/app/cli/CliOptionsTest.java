package com.parallelimage.app.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.pipeline.ImageOperation;
import com.parallelimage.core.pipeline.PipelineFormat;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link CliOptions} tests, organised around the two-phase contract.
 *
 * <p>The interesting assertions are not "does {@code -i} set the input" but the three properties the
 * split exists to provide: {@code parse} rejects only what it cannot turn into values, {@code validate}
 * reports <em>every</em> semantic problem at once, and {@code --help} survives a line that is otherwise
 * nonsense. Those are the ones that break silently when someone adds a flag.
 *
 * <p>{@link UsageText} is the unusual one, and the reason this class exists at all: {@code usage()} once
 * documented a pipeline grammar that {@link PipelineFormat} does not accept — a {@code |} separator, a
 * {@code brightness} operation, a fractional blur radius. Nothing failed, because help text is a string
 * constant that no code reads. Feeding the documented examples through the real parser makes the next
 * such drift a build failure.
 */
class CliOptionsTest {

    @Nested
    @DisplayName("parse")
    class Parsing {

        @Test
        @DisplayName("long and short forms produce the same options")
        void longAndShortAgree() {
            CliOptions longForm = CliOptions.parse(new String[] {
                    "--in", "in", "--out", "out", "--pipeline", "grayscale", "--format", "png",
                    "--recursive", "--quiet"});
            CliOptions shortForm = CliOptions.parse(new String[] {
                    "-i", "in", "-o", "out", "-p", "grayscale", "-f", "png", "-r", "-q"});
            assertEquals(longForm, shortForm);
        }

        @Test
        @DisplayName("paths are absolute and normalised, so validate() compares like with like")
        void pathsAreNormalised() {
            CliOptions options = CliOptions.parse(new String[] {"-i", "photos/../photos", "-o", "web"});
            Path input = options.input().orElseThrow();
            assertTrue(input.isAbsolute(), "not absolutised: " + input);
            assertEquals("photos", input.getFileName().toString(), "not normalised: " + input);
        }

        @Test
        @DisplayName("defaults: history on, everything else off")
        void defaults() {
            CliOptions options = CliOptions.parse(new String[0]);
            assertTrue(options.history(), "history is opt-out, not opt-in");
            assertEquals("", options.pipelineSpec());
            assertEquals(0, options.parallelism());
            assertFalse(options.recursive() || options.overwrite() || options.stripMetadata()
                    || options.quiet() || options.dryRun() || options.help() || options.version());
            assertTrue(options.input().isEmpty() && options.output().isEmpty()
                    && options.format().isEmpty() && options.quality().isEmpty());
        }

        @Test
        @DisplayName("--no-history is the only flag that turns something off")
        void noHistory() {
            assertFalse(CliOptions.parse(new String[] {"--no-history"}).history());
        }

        @Test
        @DisplayName("--dry-run turns on dryRun and nothing else")
        void dryRun() {
            CliOptions options = CliOptions.parse(new String[] {"--dry-run"});
            assertTrue(options.dryRun());
            assertFalse(options.recursive() || options.overwrite() || options.stripMetadata()
                    || options.quiet());
        }

        @ParameterizedTest
        @ValueSource(strings = {"--overwite", "--input", "--threads", "-j", "-x", "extra-positional"})
        @DisplayName("an unrecognised token is refused, never ignored")
        void unknownTokensAreRefused(String arg) {
            CliOptions.CliSyntaxException thrown = assertThrows(CliOptions.CliSyntaxException.class,
                    () -> CliOptions.parse(new String[] {arg}));
            assertTrue(thrown.getMessage().contains(arg), thrown.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"--input", "--output", "--ops", "--threads", "--force"})
        @DisplayName("a plausible typo of a long flag is named in the message")
        void typosSuggestTheRealFlag(String arg) {
            CliOptions.CliSyntaxException thrown = assertThrows(CliOptions.CliSyntaxException.class,
                    () -> CliOptions.parse(new String[] {arg, "value"}));
            assertTrue(thrown.getMessage().contains("did you mean"), thrown.getMessage());
        }

        @Test
        @DisplayName("a flag at the end of the line is missing its value, not silently empty")
        void trailingFlagWithoutValue() {
            CliOptions.CliSyntaxException thrown = assertThrows(CliOptions.CliSyntaxException.class,
                    () -> CliOptions.parse(new String[] {"-i", "photos", "--out"}));
            assertTrue(thrown.getMessage().contains("--out"), thrown.getMessage());
        }

        @Test
        @DisplayName("a flag followed by another flag blames the flag that is missing its value")
        void flagFollowedByFlag() {
            // "--in --out /tmp/x" must not bind "--out" as the input directory and then complain that
            // --out is absent, which points the operator at the wrong half of the line.
            CliOptions.CliSyntaxException thrown = assertThrows(CliOptions.CliSyntaxException.class,
                    () -> CliOptions.parse(new String[] {"--in", "--out", "/tmp/x"}));
            assertTrue(thrown.getMessage().startsWith("--in"), thrown.getMessage());
        }

        @Test
        @DisplayName("a bare '-' is a value, not a flag")
        void bareDashIsAValue() {
            // Length-one is the exception in value(): "-" is a conventional stand-in for stdin and could
            // plausibly become one, whereas "-r" never means a directory.
            assertEquals("-", CliOptions.parse(new String[] {"-p", "-"}).pipelineSpec());
        }

        @Test
        @DisplayName("--format is case-insensitive and normalised to lower case")
        void formatIsNormalised() {
            assertEquals("jpg", CliOptions.parse(new String[] {"-f", " JPG "}).format().orElseThrow());
        }

        @Test
        @DisplayName("an unsupported --format lists the ones that work")
        void unsupportedFormat() {
            CliOptions.CliSyntaxException thrown = assertThrows(CliOptions.CliSyntaxException.class,
                    () -> CliOptions.parse(new String[] {"-f", "tiff"}));
            assertTrue(thrown.getMessage().contains("png"), thrown.getMessage());
        }

        @Test
        @DisplayName("--quality 85 is caught at parse time with the fix in the message")
        void qualityOnAHundredScale() {
            // The single most likely mistake: JPEG quality is 0-100 everywhere else in the world.
            CliOptions.CliSyntaxException thrown = assertThrows(CliOptions.CliSyntaxException.class,
                    () -> CliOptions.parse(new String[] {"--quality", "85"}));
            assertTrue(thrown.getMessage().contains("0.85"), thrown.getMessage());
        }

        @Test
        @DisplayName("--quality accepts the closed range 0.0..1.0")
        void qualityRange() {
            assertEquals(Optional.of(0.0f), CliOptions.parse(new String[] {"--quality", "0"}).quality());
            assertEquals(Optional.of(1.0f), CliOptions.parse(new String[] {"--quality", "1.0"}).quality());
            assertThrows(CliOptions.CliSyntaxException.class,
                    () -> CliOptions.parse(new String[] {"--quality", "-0.1"}));
        }

        @Test
        @DisplayName("--parallelism 0 is legal and means 'the engine's default'")
        void parallelismZeroIsLegal() {
            assertEquals(0, CliOptions.parse(new String[] {"--parallelism", "0"}).parallelism());
            assertThrows(CliOptions.CliSyntaxException.class,
                    () -> CliOptions.parse(new String[] {"--parallelism", "-1"}));
            assertThrows(CliOptions.CliSyntaxException.class,
                    () -> CliOptions.parse(new String[] {"--parallelism", "eight"}));
        }

        @Test
        @DisplayName("a repeated flag takes the last value; the CLI is not a set")
        void lastFlagWins() {
            assertEquals("png", CliOptions.parse(new String[] {"-f", "jpg", "-f", "png"})
                    .format().orElseThrow());
        }

        @Test
        @DisplayName("--help parses even when the rest of the line is otherwise complete nonsense")
        void helpSurvivesGarbage() {
            // The whole point of not validating during parse. --pipeline "nope" is unparseable *semantically*
            // -- validate() will say so -- but it must not stop --help from being seen.
            CliOptions options = CliOptions.parse(new String[] {"--help", "-p", "nope", "-i", "x"});
            assertTrue(options.help());
        }
    }

    @Nested
    @DisplayName("validate")
    class Validation {

        @Test
        @DisplayName("a complete line has no problems")
        void happyPath() {
            assertEquals(List.of(), CliOptions
                    .parse(new String[] {"-i", "photos", "-o", "web", "-p", "grayscale>sharpen:0.5"})
                    .validate());
        }

        @Test
        @DisplayName("every problem is reported in one pass, not one per run")
        void allProblemsAtOnce() {
            List<String> problems = CliOptions
                    .parse(new String[] {"-p", "brightness:2", "-f", "png", "--quality", "0.9"})
                    .validate();
            assertEquals(4, problems.size(), "expected --in, --out, --pipeline and --quality: " + problems);
            assertTrue(problems.stream().anyMatch(p -> p.contains("--in is required")), problems.toString());
            assertTrue(problems.stream().anyMatch(p -> p.contains("--out is required")), problems.toString());
            assertTrue(problems.stream().anyMatch(p -> p.startsWith("--pipeline:")), problems.toString());
            assertTrue(problems.stream().anyMatch(p -> p.startsWith("--quality has no effect")),
                    problems.toString());
        }

        @Test
        @DisplayName("identical --in and --out is an error, not a warning")
        void inputEqualsOutput() {
            List<String> problems = CliOptions.parse(new String[] {"-i", "photos", "-o", "photos/."})
                    .validate();
            assertEquals(1, problems.size(), problems.toString());
            assertTrue(problems.get(0).contains("must differ"), problems.toString());
        }

        @Test
        @DisplayName("--quality on a lossless format is refused rather than silently dropped")
        void qualityOnLosslessFormat() {
            for (String lossless : List.of("png", "bmp", "gif")) {
                List<String> problems = CliOptions
                        .parse(new String[] {"-i", "a", "-o", "b", "-f", lossless, "--quality", "0.8"})
                        .validate();
                assertEquals(1, problems.size(), lossless + ": " + problems);
            }
            for (String lossy : List.of("jpg", "jpeg", "webp")) {
                assertEquals(List.of(), CliOptions
                        .parse(new String[] {"-i", "a", "-o", "b", "-f", lossy, "--quality", "0.8"})
                        .validate(), lossy);
            }
        }

        @Test
        @DisplayName("--quality without --format is not second-guessed")
        void qualityWithoutFormat() {
            // The output format then comes from AppConfig, which this class cannot see. Guessing would
            // produce an error message about a format the operator never typed.
            assertEquals(List.of(), CliOptions
                    .parse(new String[] {"-i", "a", "-o", "b", "--quality", "0.8"}).validate());
        }

        @Test
        @DisplayName("an empty pipeline is valid: transcoding is a legitimate batch")
        void emptyPipelineIsValid() {
            assertEquals(List.of(),
                    CliOptions.parse(new String[] {"-i", "a", "-o", "b", "-f", "webp"}).validate());
        }
    }

    @Nested
    @DisplayName("toProcessingOptions")
    class Projection {

        private static final ProcessingOptions DEFAULTS = ProcessingOptions.builder()
                .operations(List.of(new ImageOperation.Grayscale()))
                .outputFormat("webp")
                .quality(0.6f)
                .tileThresholdPixels(1_000_000L)
                .batchThresholdJobs(16)
                .stripMetadata(true)
                .overwriteExisting(true)
                .maxPixelsPerImage(100_000_000L)
                .build();

        @Test
        @DisplayName("an absent flag inherits the configured default rather than a compiled-in one")
        void absentFlagsInherit() {
            ProcessingOptions options = CliOptions.parse(new String[] {"-i", "a", "-o", "b"})
                    .toProcessingOptions(DEFAULTS);
            assertEquals(DEFAULTS, options);
        }

        @Test
        @DisplayName("a present flag overrides")
        void presentFlagsOverride() {
            ProcessingOptions options = CliOptions
                    .parse(new String[] {"-i", "a", "-o", "b", "-f", "jpg", "--quality", "0.95",
                            "-p", "resize:800x600:fit"})
                    .toProcessingOptions(DEFAULTS);
            assertEquals("jpg", options.outputFormat());
            assertEquals(0.95f, options.quality());
            assertEquals(List.of(new ImageOperation.Resize(800, 600, true)), options.operations());
        }

        @Test
        @DisplayName("tuning knobs are never taken from the command line")
        void tuningIsConfigOnly() {
            // There are no flags for these on purpose: they are properties of the machine, not of the
            // batch, so they must survive any invocation.
            ProcessingOptions options = CliOptions.parse(new String[] {"-i", "a", "-o", "b"})
                    .toProcessingOptions(DEFAULTS);
            assertEquals(DEFAULTS.tileThresholdPixels(), options.tileThresholdPixels());
            assertEquals(DEFAULTS.batchThresholdJobs(), options.batchThresholdJobs());
            assertEquals(DEFAULTS.maxPixelsPerImage(), options.maxPixelsPerImage());
        }

        @Test
        @DisplayName("boolean flags can only turn things on, so a default true is never cleared")
        void booleansAreOnlyAdditive() {
            ProcessingOptions options = CliOptions.parse(new String[] {"-i", "a", "-o", "b"})
                    .toProcessingOptions(DEFAULTS);
            assertTrue(options.stripMetadata());
            assertTrue(options.overwriteExisting());
        }

        @Test
        @DisplayName("a default-off boolean is turned on by its flag")
        void booleansTurnOn() {
            ProcessingOptions off = ProcessingOptions.builder()
                    .operations(List.of())
                    .outputFormat("png")
                    .quality(0.9f)
                    .tileThresholdPixels(DEFAULTS.tileThresholdPixels())
                    .batchThresholdJobs(DEFAULTS.batchThresholdJobs())
                    .stripMetadata(false)
                    .overwriteExisting(false)
                    .maxPixelsPerImage(DEFAULTS.maxPixelsPerImage())
                    .build();
            ProcessingOptions options = CliOptions
                    .parse(new String[] {"-i", "a", "-o", "b", "--overwrite", "--strip-metadata"})
                    .toProcessingOptions(off);
            assertTrue(options.stripMetadata() && options.overwriteExisting());
        }
    }

    @Nested
    @DisplayName("usage text")
    class UsageText {

        private static final String USAGE = CliOptions.usage();

        @Test
        @DisplayName("every flag the parser accepts appears in the help")
        void allFlagsAreDocumented() {
            List<String> flags = List.of("--in", "--out", "--pipeline", "--format", "--quality",
                    "--parallelism", "--recursive", "--overwrite", "--strip-metadata", "--no-history",
                    "--dry-run", "--quiet", "--help", "--version", "--ui", "--serve");
            flags.forEach(flag -> assertTrue(USAGE.contains(flag), "undocumented flag: " + flag));
        }

        @Test
        @DisplayName("the documented pipeline example actually parses")
        void documentedExampleParses() {
            // Extracted from the Example block rather than restated, so editing the help without editing
            // this test cannot make the two agree by accident.
            String spec = between(USAGE, "-p \"", "\"");
            assertEquals(List.of(new ImageOperation.Resize(1600, 1600, true),
                    new ImageOperation.Sharpen(0.3d)), PipelineFormat.parse(spec), spec);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "grayscale", "greyscale", "resize:640x480", "resize:640x480:fit",
                "blur:1", "blur:64", "sharpen:0.0", "sharpen:5.0",
                "enhance:clahe", "enhance:denoise", "enhance:super_resolution", "enhance:clahe:0.5"})
        @DisplayName("every operation and bound the help advertises is accepted by the parser")
        void advertisedGrammarIsReal(String spec) {
            assertEquals(1, PipelineFormat.parse(spec).size(), spec);
        }

        @ParameterizedTest
        @ValueSource(strings = {"blur:0", "blur:65", "blur:2.5", "sharpen:5.1", "enhance:upscale",
                "brightness:2", "watermark:hello"})
        @DisplayName("what the help does not advertise, the parser does not accept")
        void unadvertisedGrammarIsRejected(String spec) {
            // The other half of the contract. Documenting a narrower grammar than the parser accepts is
            // harmless; documenting a wider one is what produced the last round of corrections here.
            assertThrows(IllegalArgumentException.class, () -> PipelineFormat.parse(spec), spec);
        }

        @Test
        @DisplayName("the stage and argument separators named in the help are the real ones")
        void separatorsMatch() {
            assertTrue(USAGE.contains("stages separated by '>', arguments by ':'"), USAGE);
            assertEquals(2, PipelineFormat.parse("grayscale>blur:2").size());
        }

        @Test
        @DisplayName("watermarks are documented as UI-only, because parse() refuses them")
        void watermarksAreDocumentedAsUiOnly() {
            assertTrue(USAGE.contains("Watermarks are not expressible here"), USAGE);
        }

        @Test
        @DisplayName("every exit code CliRunner can return is listed")
        void exitCodesAreDocumented() {
            for (int code : new int[] {CliRunner.EXIT_OK, CliRunner.EXIT_FAILURES,
                    CliRunner.EXIT_USAGE, CliRunner.EXIT_STARTUP}) {
                assertTrue(USAGE.contains(String.valueOf(code)), "exit code " + code + " not documented");
            }
        }

        @Test
        @DisplayName("the help is pure ASCII, because Windows consoles encode with the codepage")
        void helpIsAscii() {
            // An en dash in "0.0-1.0" prints as '?' in cmd.exe, and help text that renders as garbage is
            // worse than help text that is plain. Cheap to assert, easy to regress in a one-line edit.
            USAGE.chars().forEach(c -> assertTrue(c < 0x80,
                    "non-ASCII U+%04X in usage()".formatted(c)));
        }

        private static String between(String text, String open, String close) {
            int from = text.indexOf(open);
            assertTrue(from >= 0, "usage() no longer contains " + open);
            int to = text.indexOf(close, from + open.length());
            assertTrue(to > from, "unterminated " + open + " in usage()");
            return text.substring(from + open.length(), to);
        }
    }
}
