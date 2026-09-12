package com.parallelimage.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.pipeline.ImageOperation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link JobRequestValidator} tests.
 *
 * <p>Every rule here exists because of a specific way an untrusted body can produce a batch that does
 * something other than what was asked, so each test names that outcome rather than the rule. The two the
 * suite spends the most effort on are the ones whose failure is silent: <b>an unknown field</b> (a
 * typo'd {@code overwite} that is ignored is an operator who believes overwriting is on) and <b>a
 * non-strict boolean</b> ({@code "yes"} through {@link Boolean#parseBoolean} is {@code false}, which is
 * an API that means the opposite of what it was sent).
 *
 * <p>The validator takes a {@code Map} and returns a list, so none of this needs a server socket. That
 * is the whole reason it is not part of {@code BatchJobController}.
 */
class JobRequestValidatorTest {

    /** Deliberately unlike {@link ProcessingOptions#defaults()}, so inheritance is visible when it works. */
    private static final ProcessingOptions DEFAULTS = ProcessingOptions.builder()
            .operations(List.of(new ImageOperation.Grayscale()))
            .outputFormat("webp")
            .quality(0.6f)
            .tileThresholdPixels(250_000L)
            .batchThresholdJobs(32)
            .stripMetadata(true)
            .overwriteExisting(true)
            .maxPixelsPerImage(50_000_000L)
            .build();

    @TempDir
    private Path root;

    private Path input;
    private Path output;

    @BeforeEach
    void createDirectories() throws IOException {
        input = Files.createDirectories(root.resolve("photos"));
        output = root.resolve("web");
    }

    /** A body with the two required fields, plus whatever the caller adds. */
    private Map<String, String> body(String... keyThenValue) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("input", input.toString());
        fields.put("output", output.toString());
        for (int i = 0; i < keyThenValue.length; i += 2) {
            fields.put(keyThenValue[i], keyThenValue[i + 1]);
        }
        return fields;
    }

    private static JobRequestValidator.Result validate(Map<String, String> fields) {
        return JobRequestValidator.validate(fields, DEFAULTS);
    }

    /** The single problem a body was expected to produce. Fails the test if there is not exactly one. */
    private static String onlyProblem(Map<String, String> fields) {
        JobRequestValidator.Result result = validate(fields);
        assertFalse(result.valid(), "expected the body to be rejected");
        assertEquals(1, result.problems().size(), "expected exactly one problem: " + result.problems());
        return result.problems().get(0);
    }

    @Nested
    @DisplayName("a valid submission")
    class Valid {

        @Test
        @DisplayName("the two required fields are enough, and everything else is inherited")
        void minimalBody() {
            JobRequestValidator.Result result = validate(body());
            assertEquals(List.of(), result.problems());
            assertTrue(result.valid());

            JobRequestValidator.BatchRequest request = result.request().orElseThrow();
            assertEquals(input, request.input());
            assertEquals(output, request.output());
            assertFalse(request.recursive(), "recursive defaults off: a surprise walk is expensive");
            assertEquals(DEFAULTS, request.options(),
                    "an unmentioned field must inherit the configured default, not a compiled-in one");
        }

        @Test
        @DisplayName("the output directory need not exist yet")
        void outputMayBeAbsent() {
            assertFalse(Files.exists(output));
            assertTrue(validate(body()).valid());
        }

        @Test
        @DisplayName("a mentioned field overrides")
        void fieldsOverride() {
            ProcessingOptions options = validate(body(
                    "pipeline", "resize:800x600:fit>sharpen:0.4",
                    "format", "jpg",
                    "quality", "0.75",
                    "overwrite", "false",
                    "stripMetadata", "false",
                    "recursive", "true")).request().orElseThrow().options();

            assertEquals(List.of(new ImageOperation.Resize(800, 600, true),
                    new ImageOperation.Sharpen(0.4d)), options.operations());
            assertEquals("jpg", options.outputFormat());
            // 0.75 rather than 0.82: exactly representable, so the double-to-float narrowing in the
            // validator cannot make this assertion about anything other than the field being read.
            assertEquals(0.75f, options.quality());
            assertFalse(options.overwriteExisting(), "a false in the body must override a true default");
            assertFalse(options.stripMetadata());
        }

        @Test
        @DisplayName("the tuning thresholds are not part of the wire format")
        void tuningIsNotRequestable() {
            // Not in KNOWN_FIELDS on purpose: they describe the machine, not the batch. Sending them is a
            // rejected unknown field rather than a per-request override.
            ProcessingOptions options = validate(body()).request().orElseThrow().options();
            assertEquals(DEFAULTS.tileThresholdPixels(), options.tileThresholdPixels());
            assertEquals(DEFAULTS.batchThresholdJobs(), options.batchThresholdJobs());
            assertEquals(DEFAULTS.maxPixelsPerImage(), options.maxPixelsPerImage());
            assertTrue(onlyProblem(body("tileThresholdPixels", "4096")).contains("unknown field"));
        }

        @Test
        @DisplayName("parallelism is accepted, range-checked, and then dropped")
        void parallelismIsAcceptedThenDropped() {
            // The engine owns one pool for the process lifetime; ForkJoinPool cannot be resized, and a pool
            // per request would defeat work stealing across batches. The controller reports the value back
            // so the client can see it had no effect -- what it must not do is refuse the field.
            assertTrue(validate(body("parallelism", "8")).valid());
            assertTrue(validate(body("parallelism", "0")).valid(), "0 means 'the engine's default'");
            assertTrue(validate(body("parallelism", "256")).valid(), "the bound is inclusive");
        }
    }

    @Nested
    @DisplayName("paths")
    class Paths {

        @Test
        @DisplayName("both directories are required, and both are reported at once")
        void bothRequired() {
            List<String> problems = JobRequestValidator.validate(Map.of(), DEFAULTS).problems();
            assertEquals(2, problems.size(), problems.toString());
            assertTrue(problems.contains("input is required"), problems.toString());
            assertTrue(problems.contains("output is required"), problems.toString());
        }

        @Test
        @DisplayName("a blank value is the same as an absent one")
        void blankIsAbsent() {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("input", "   ");
            fields.put("output", output.toString());
            assertEquals("input is required", onlyProblem(fields));
        }

        @Test
        @DisplayName("a relative path is refused rather than resolved against the server's directory")
        void relativePathsAreRefused() {
            // The client cannot see the server process's working directory, and it differs between a
            // launcher script and an IDE run. "The output went somewhere" is not a debuggable report.
            Map<String, String> fields = body();
            fields.put("output", "web");
            assertTrue(onlyProblem(fields).contains("must be an absolute path"));
        }

        @Test
        @DisplayName("a non-existent input directory is caught before any work starts")
        void inputMustExist() {
            Map<String, String> fields = body();
            fields.put("input", root.resolve("nope").toString());
            assertTrue(onlyProblem(fields).contains("does not exist or is not a directory"));
        }

        @Test
        @DisplayName("a file where a directory belongs is refused")
        void inputMustBeADirectory() throws IOException {
            Path file = Files.writeString(root.resolve("one.png"), "not really a png");
            Map<String, String> fields = body();
            fields.put("input", file.toString());
            assertTrue(onlyProblem(fields).contains("not a directory"));
        }

        @Test
        @DisplayName("input and output must differ, even written differently")
        void inputAndOutputMustDiffer() {
            Map<String, String> fields = body();
            fields.put("output", input.resolve("..").resolve(input.getFileName()).toString());
            assertEquals("input and output must differ", onlyProblem(fields),
                    "the comparison must be after normalisation, or '.' and '..' defeat it");
        }

        @Test
        @DisplayName("output inside input is refused only when recursive, because only then does it loop")
        void outputInsideInput() throws IOException {
            Map<String, String> fields = body();
            fields.put("output", input.resolve("resized").toString());

            assertTrue(validate(fields).valid(),
                    "non-recursive, the walk never sees the nested directory");

            fields.put("recursive", "true");
            assertTrue(onlyProblem(fields).contains("output must not be inside input"),
                    "recursive, the walk finds the files it just wrote and re-processes them forever");
        }
    }

    @Nested
    @DisplayName("values")
    class Values {

        @Test
        @DisplayName("the pipeline grammar is PipelineFormat's, with its message passed through")
        void pipelineIsDelegated() {
            // No second implementation of the grammar here: one parser, one set of error messages. This
            // asserts the delegation, not the grammar -- PipelineFormatTest owns that.
            String problem = onlyProblem(body("pipeline", "grayscale>bogus"));
            assertTrue(problem.startsWith("pipeline: "), problem);
            assertTrue(problem.contains("bogus"), problem);
        }

        @Test
        @DisplayName("an empty pipeline is a legitimate batch: a pure format conversion")
        void emptyPipelineIsValid() {
            ProcessingOptions options = validate(body("pipeline", "", "format", "jpg"))
                    .request().orElseThrow().options();
            assertEquals(DEFAULTS.operations(), options.operations(),
                    "a blank pipeline is 'not mentioned', so the default chain stands");
        }

        @Test
        @DisplayName("the format is case-insensitive, and jpeg is stored as jpg")
        void formatIsNormalised() {
            assertEquals("jpg", validate(body("format", "JPEG")).request().orElseThrow()
                    .options().outputFormat());
        }

        @Test
        @DisplayName("an unsupported format lists the ones that work")
        void unsupportedFormat() {
            String problem = onlyProblem(body("format", "tiff"));
            assertTrue(problem.contains("tiff") && problem.contains("png"), problem);
        }

        @ParameterizedTest
        @DisplayName("quality outside 0.0..1.0 is refused with the bounds in the message")
        @ValueSource(strings = {"-0.1", "1.1", "85"})
        void qualityRange(String value) {
            String problem = onlyProblem(body("quality", value));
            assertTrue(problem.startsWith("quality must be between 0.0 and 1.0"), problem);
        }

        @Test
        @DisplayName("quality accepts both ends of the range")
        void qualityBoundsAreInclusive() {
            assertEquals(0.0f, validate(body("quality", "0")).request().orElseThrow().options().quality());
            assertEquals(1.0f, validate(body("quality", "1")).request().orElseThrow().options().quality());
        }

        @Test
        @DisplayName("NaN quality is refused here, not left to blow up in the core")
        void nanQualityIsRefused() {
            // Every comparison against NaN is false, so a range check written as "below min or above max"
            // lets it through -- and then ProcessingOptions throws IllegalArgumentException, which the
            // controller cannot tell from a genuine bug. A mistyped quality must answer 400, never 500.
            // (Json.parseFlatObject also refuses the token; this covers the validator on its own terms.)
            assertTrue(onlyProblem(body("quality", "NaN")).startsWith("quality must be between"));
        }

        @Test
        @DisplayName("a non-numeric quality names the field and the value")
        void qualityMustBeANumber() {
            String problem = onlyProblem(body("quality", "high"));
            assertTrue(problem.contains("quality must be a number") && problem.contains("high"), problem);
        }

        @ParameterizedTest
        @DisplayName("parallelism outside 0..256 is refused")
        @ValueSource(strings = {"-1", "257", "1000000"})
        void parallelismRange(String value) {
            assertTrue(onlyProblem(body("parallelism", value)).startsWith("parallelism must be between 0 and 256"));
        }

        @Test
        @DisplayName("a fractional parallelism is refused rather than truncated")
        void parallelismMustBeAnInteger() {
            assertTrue(onlyProblem(body("parallelism", "4.5")).contains("must be an integer"));
        }
    }

    @Nested
    @DisplayName("strictness")
    class Strictness {

        @ParameterizedTest
        @DisplayName("only true and false are booleans; yes/on/1 are refused, not reinterpreted")
        @ValueSource(strings = {"yes", "no", "on", "off", "1", "0", "y", "enabled"})
        void booleansAreStrict(String value) {
            // Boolean.parseBoolean("yes") is false. An API that accepts a value and does the opposite of
            // what it was sent is worse than one that refuses it.
            String problem = onlyProblem(body("overwrite", value));
            assertTrue(problem.startsWith("overwrite must be true or false"), problem);
            assertTrue(problem.contains(value), problem);
        }

        @ParameterizedTest
        @DisplayName("the two spellings that are accepted are case-insensitive")
        @ValueSource(strings = {"true", "TRUE", "True", "false", "FALSE", "False"})
        void booleanCaseIsIgnored(String value) {
            assertTrue(validate(body("recursive", value)).valid(), value);
        }

        @Test
        @DisplayName("every boolean field is read the same way")
        void allBooleansAreStrict() {
            for (String field : List.of("recursive", "overwrite", "stripMetadata")) {
                assertTrue(onlyProblem(body(field, "yes")).startsWith(field + " must be true or false"),
                        field);
            }
        }

        @ParameterizedTest
        @DisplayName("an unknown field is refused, because a silently ignored one is a support call")
        @ValueSource(strings = {"overwite", "strip_metadata", "outputFormat", "threads", "in", "Input"})
        void unknownFieldsAreRefused(String field) {
            // "Input" is in the list on purpose: the field names are case-sensitive, and a client sending
            // the wrong case would otherwise have its input directory ignored and be told it was required.
            String problem = onlyProblem(body(field, "true"));
            assertTrue(problem.startsWith("unknown field '" + field + "'"), problem);
            assertTrue(problem.contains("stripMetadata"), "the message must list what is accepted: " + problem);
        }

        @Test
        @DisplayName("every problem in a thoroughly wrong body is reported in one response")
        void allProblemsAtOnce() {
            // A client with eight mistakes must not need eight round trips to find them.
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("input", "relative/in");     // not absolute
            fields.put("pipeline", "bogus");        // unknown stage
            fields.put("format", "tiff");           // unsupported
            fields.put("quality", "2");             // out of range
            fields.put("parallelism", "999");       // out of range
            fields.put("recursive", "yes");         // not a boolean
            fields.put("overwite", "true");         // unknown field
            // "output" omitted                     // required

            List<String> problems = validate(fields).problems();
            assertEquals(8, problems.size(), problems.toString());
        }

        @Test
        @DisplayName("a rejected body carries no request, and an accepted one carries no problems")
        void resultIsOneOrTheOther() {
            // Result is a pair rather than a sealed hierarchy, so this invariant is worth asserting once.
            JobRequestValidator.Result rejected = validate(body("format", "tiff"));
            assertFalse(rejected.valid());
            assertTrue(rejected.request().isEmpty(),
                    "a half-built request would be usable by mistake on the failure path");

            JobRequestValidator.Result accepted = validate(body());
            assertTrue(accepted.valid());
            assertEquals(List.of(), accepted.problems());
        }
    }
}
