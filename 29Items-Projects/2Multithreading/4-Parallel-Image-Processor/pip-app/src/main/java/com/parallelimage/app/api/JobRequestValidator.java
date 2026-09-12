package com.parallelimage.app.api;

import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.pipeline.ImageOperation;
import com.parallelimage.core.pipeline.PipelineFormat;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Turns an untrusted request body into a validated batch submission, or into a list of reasons why not.
 *
 * <h2>Why this is a separate class from the controller</h2>
 * The controller's job is HTTP: methods, status codes, headers, streams. This class's job is judgement
 * about values. Keeping them apart means every validation rule can be unit-tested by passing a map and
 * reading a list — no server socket, no request object, no fixtures. It also stops the two concerns from
 * being interleaved, which is how a check ends up applied on one code path and not another.
 *
 * <h2>The rules, and what each one prevents</h2>
 * <ol>
 *   <li><b>Both directories required, and absolute.</b> A relative path would resolve against the server
 *       process's working directory, which the client cannot see and which differs between a launcher
 *       script and an IDE run. "{@code out} went somewhere" is not a debuggable report.</li>
 *   <li><b>Input must exist and be a directory.</b> Cheap to check, and the alternative is a batch that
 *       reports zero images with no explanation.</li>
 *   <li><b>Input and output must differ.</b> Same reasoning as the CLI: with overwrite enabled this
 *       re-encodes every source in place through a lossy encoder; without it, every job is skipped.</li>
 *   <li><b>Output must not be inside input</b> when recursive. Otherwise the walk finds the files it has
 *       just written and processes them again, which does not terminate in any useful sense — the
 *       directory grows for as long as the disk allows.</li>
 *   <li><b>Pipeline delegated to {@link PipelineFormat}.</b> One grammar, one parser, one error message.</li>
 *   <li><b>Numeric ranges checked here</b>, not left to the core model's preconditions. The core throws
 *       {@link IllegalArgumentException}, which a controller cannot distinguish from a genuine bug; a
 *       message on a list becomes a 400 with a field name in it.</li>
 * </ol>
 *
 * <h2>What this class explicitly does not do</h2>
 * It does not sandbox the paths. A caller with access to the loopback port can already read and write
 * anything the JVM's user can, so an allow-list here would be security theatre — it would suggest a
 * boundary that does not exist. The actual boundary is the loopback bind and the optional token in
 * {@code BatchJobController}; see ARCHITECTURE §2.5. If this API were ever exposed beyond localhost,
 * path confinement would have to be added <em>and</em> the process would need to run as a restricted user.
 */
public final class JobRequestValidator {

    /** Same set the CLI accepts, for the same reason: what {@code ImageSink} can actually write. */
    private static final List<String> FORMATS = List.of("png", "jpg", "jpeg", "webp", "bmp", "gif");

    /** Upper bound on requested workers. Beyond this, threads cost more than they contribute. */
    private static final int MAX_PARALLELISM = 256;

    private JobRequestValidator() {
    }

    /**
     * A validated submission. Only constructed when there are no problems, so every field is usable.
     *
     * @param input     existing source directory, absolute and normalised
     * @param output    destination directory, absolute and normalised; may not exist yet
     * @param options   fully-resolved processing options
     * @param recursive whether to descend into subdirectories
     */
    public record BatchRequest(Path input, Path output, ProcessingOptions options, boolean recursive) {
    }

    /**
     * The result of validating a body: one or the other, never both.
     *
     * <p>A sealed-style pair rather than an exception because the caller needs <em>all</em> the problems to
     * put in the response. Throwing on the first one means a client with three mistakes makes three
     * round trips to find out.
     */
    public record Result(Optional<BatchRequest> request, List<String> problems) {

        public Result {
            problems = List.copyOf(problems);
        }

        public boolean valid() {
            return request.isPresent();
        }
    }

    /**
     * Validates a parsed request body against the configured defaults.
     *
     * @param fields   flat map from {@link Json#parseFlatObject}
     * @param defaults options to inherit for anything the request does not mention
     */
    public static Result validate(Map<String, String> fields, ProcessingOptions defaults) {
        List<String> problems = new ArrayList<>();

        Path input = directory(fields, "input", problems);
        Path output = directory(fields, "output", problems);

        if (input != null && !Files.isDirectory(input)) {
            problems.add("input does not exist or is not a directory: " + input);
        }
        boolean recursive = bool(fields, "recursive", false, problems);

        if (input != null && output != null) {
            if (input.equals(output)) {
                problems.add("input and output must differ");
            } else if (recursive && output.startsWith(input)) {
                problems.add("output must not be inside input when recursive is true "
                        + "(the walk would keep finding its own results)");
            }
        }

        List<ImageOperation> operations = defaults.operations();
        Optional<String> spec = Json.text(fields, "pipeline");
        if (spec.isPresent()) {
            try {
                operations = PipelineFormat.parse(spec.get());
            } catch (IllegalArgumentException e) {
                problems.add("pipeline: " + e.getMessage());
            }
        }

        String format = defaults.outputFormat();
        Optional<String> requestedFormat = Json.text(fields, "format");
        if (requestedFormat.isPresent()) {
            String lower = requestedFormat.get().toLowerCase(Locale.ROOT);
            if (FORMATS.contains(lower)) {
                format = lower;
            } else {
                problems.add("unsupported format '" + requestedFormat.get() + "'; expected one of " + FORMATS);
            }
        }

        float quality = (float) number(fields, "quality", defaults.quality(), 0.0d, 1.0d, problems);
        // Called for its range check, not its value -- see the note further down on why the value is
        // dropped. The result is deliberately not assigned: a variable nothing reads invites the next
        // reader to wire it into the builder below, which is exactly the change that must not happen.
        integer(fields, "parallelism", 0, 0, MAX_PARALLELISM, problems);
        boolean overwrite = bool(fields, "overwrite", defaults.overwriteExisting(), problems);
        boolean strip = bool(fields, "stripMetadata", defaults.stripMetadata(), problems);

        rejectUnknownFields(fields, problems);

        if (!problems.isEmpty()) {
            return new Result(Optional.empty(), List.copyOf(problems));
        }

        ProcessingOptions options = ProcessingOptions.builder()
                .operations(operations)
                .outputFormat(format)
                .quality(quality)
                .tileThresholdPixels(defaults.tileThresholdPixels())
                .batchThresholdJobs(defaults.batchThresholdJobs())
                .stripMetadata(strip)
                .overwriteExisting(overwrite)
                .maxPixelsPerImage(defaults.maxPixelsPerImage())
                .build();

        // parallelism is accepted, range-checked, and then deliberately dropped: the engine owns one pool
        // for the process lifetime, and honouring a per-request worker count would mean either building a
        // pool per request (defeating work-stealing across batches) or resizing a live pool (which
        // ForkJoinPool does not support). Silently ignoring it would be worse -- see the controller, which
        // reports it back in the response so the client can see it had no effect.
        return new Result(Optional.of(new BatchRequest(input, output, options, recursive)), List.of());
    }

    /**
     * Field names this endpoint knows.
     *
     * <p>Used to reject anything else. A typo'd {@code "overwite": true} that is silently ignored is a
     * client that believes it enabled overwriting and a support conversation that starts three weeks later.
     */
    private static final List<String> KNOWN_FIELDS = List.of(
            "input", "output", "pipeline", "format", "quality",
            "parallelism", "recursive", "overwrite", "stripMetadata");

    private static void rejectUnknownFields(Map<String, String> fields, List<String> problems) {
        fields.keySet().stream()
                .filter(key -> !KNOWN_FIELDS.contains(key))
                .forEach(key -> problems.add("unknown field '" + key + "'; expected one of " + KNOWN_FIELDS));
    }

    // ------------------------------------------------------------------------
    //  Field readers. Each adds a message and returns a usable value.
    // ------------------------------------------------------------------------

    /** Reads a required absolute directory path. Returns {@code null} when it could not be read. */
    private static Path directory(Map<String, String> fields, String key, List<String> problems) {
        Optional<String> raw = Json.text(fields, key);
        if (raw.isEmpty()) {
            problems.add(key + " is required");
            return null;
        }
        try {
            Path path = Path.of(raw.get());
            if (!path.isAbsolute()) {
                problems.add(key + " must be an absolute path (got '" + raw.get() + "')");
                return null;
            }
            return path.normalize();
        } catch (InvalidPathException e) {
            problems.add(key + " is not a valid path: " + e.getMessage());
            return null;
        }
    }

    private static double number(Map<String, String> fields, String key, double fallback,
            double min, double max, List<String> problems) {
        Optional<String> raw = Json.text(fields, key);
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(raw.get());
            // Written as a negated in-range test, not "below min or above max": the latter is false for
            // NaN, which Double.parseDouble accepts from the text "NaN". That would send NaN to the core,
            // whose preconditions do reject it -- as an IllegalArgumentException the controller cannot tell
            // from a genuine bug, so a mistyped quality would answer 500 instead of 400.
            if (!(parsed >= min && parsed <= max)) {
                problems.add(key + " must be between " + min + " and " + max + " (got " + raw.get() + ")");
                return fallback;
            }
            return parsed;
        } catch (NumberFormatException e) {
            problems.add(key + " must be a number (got '" + raw.get() + "')");
            return fallback;
        }
    }

    private static int integer(Map<String, String> fields, String key, int fallback,
            int min, int max, List<String> problems) {
        Optional<String> raw = Json.text(fields, key);
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw.get());
            if (parsed < min || parsed > max) {
                problems.add(key + " must be between " + min + " and " + max + " (got " + parsed + ")");
                return fallback;
            }
            return parsed;
        } catch (NumberFormatException e) {
            problems.add(key + " must be an integer (got '" + raw.get() + "')");
            return fallback;
        }
    }

    /**
     * Reads a boolean strictly.
     *
     * <p>Only {@code true} and {@code false}. Not {@link Boolean#parseBoolean}, which turns
     * {@code "yes"} into {@code false} — an API that accepts a value and means the opposite of what was
     * sent is worse than one that rejects it.
     */
    private static boolean bool(Map<String, String> fields, String key, boolean fallback,
            List<String> problems) {
        Optional<String> raw = Json.text(fields, key);
        if (raw.isEmpty()) {
            return fallback;
        }
        return switch (raw.get().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> {
                problems.add(key + " must be true or false (got '" + raw.get() + "')");
                yield fallback;
            }
        };
    }
}
