package com.parallelimage.app.cli;

import com.parallelimage.app.i18n.Messages;
import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.pipeline.ImageOperation;
import com.parallelimage.core.pipeline.PipelineFormat;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The parsed command line. Pure data with a static parser and no side effects.
 *
 * <h2>Why hand-rolled and not picocli</h2>
 * The surface is eleven flags with no subcommands, no completion, and no localisation. Against that, a
 * parsing library is a dependency in the distribution zip, an annotation processor or a reflective scan at
 * startup, and a second place where the {@code --help} text lives. The whole parser below is one loop and
 * a {@code switch}; if the CLI ever grows subcommands, that is the moment to reconsider, not now.
 *
 * <h2>Parse, then validate — two phases, on purpose</h2>
 * {@link #parse(String[])} never touches the filesystem and never throws for a <em>semantic</em> problem;
 * it only rejects input it cannot turn into values at all (an unknown flag, a missing argument, a
 * malformed number). {@link #validate()} then reports the semantic problems as a list.
 *
 * <p>The split is what allows {@code --help} to work even when the rest of the line is nonsense, and it
 * is what allows the CLI to print <em>every</em> problem at once rather than making the operator fix five
 * mistakes in five runs. A parser that validated as it went could not do either.
 *
 * <h2>Grammar</h2>
 * <pre>
 * pip [options] --in DIR --out DIR
 *
 *   --in, -i DIR         source directory (required unless --help/--version)
 *   --out, -o DIR        destination directory (required, must differ from --in)
 *   --pipeline, -p SPEC  operations, e.g. "grayscale&gt;resize:1920x1080:fit&gt;sharpen:0.4"
 *   --format, -f EXT     output format: png jpg webp bmp gif
 *   --quality Q          0.0-1.0, lossy formats only
 *   --parallelism N      worker threads; 0 or absent means the engine's default
 *   --recursive, -r      descend into subdirectories
 *   --overwrite          replace existing output files instead of skipping them
 *   --strip-metadata     drop EXIF and friends from the output
 *   --no-history         do not record anything in the SQLite database
 *   --dry-run            plan the batch and print it, but write nothing
 *   --quiet, -q          only print the final summary line
 *   --help, -h           print usage and exit 0
 *   --version, -V        print version and exit 0
 * </pre>
 */
public record CliOptions(
        Optional<Path> input,
        Optional<Path> output,
        String pipelineSpec,
        Optional<String> format,
        Optional<Float> quality,
        int parallelism,
        boolean recursive,
        boolean overwrite,
        boolean stripMetadata,
        boolean history,
        boolean quiet,
        boolean dryRun,
        boolean help,
        boolean version) {

    /**
     * Thrown for input that cannot be parsed at all, as opposed to input that parses but makes no sense.
     *
     * <p>A distinct type rather than {@link IllegalArgumentException} so {@code CliRunner} can print a
     * usage hint for it and let genuine programming errors propagate with their stack trace intact.
     */
    public static final class CliSyntaxException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        public CliSyntaxException(String message) {
            super(message);
        }

        /**
         * For a usage error a conversion failure revealed first.
         *
         * <p>{@code Main} prints {@link #getMessage()} and exits 2 — never a stack trace, because a
         * trace in front of "--quality must be a number" reads as a crash in the tool rather than a
         * mistake in the command line. The cause is carried regardless: it costs one field, it is what
         * an embedder calling {@link CliOptions#parse} from a test or a script sees, and dropping it
         * would leave nothing at all to look at if one of these conversions ever threw for a reason
         * the message does not describe.
         */
        public CliSyntaxException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Accepted {@code --format} values. Matches {@code ImageSink}; a wider set fails later, at write. */
    private static final List<String> FORMATS = List.of("png", "jpg", "jpeg", "webp", "bmp", "gif");

    /**
     * Parses argv.
     *
     * @throws CliSyntaxException on an unknown flag, a flag missing its argument, or an unparseable number
     */
    // Cyclomatic complexity is 24 against a project threshold of 20, and every unit of it is a flag name.
    // The switch below has thirteen arms carrying twenty-one accepted spellings, and PMD counts each
    // `case` label as a decision -- so the metric here measures the size of the command-line surface, not
    // the depth of any reasoning about it. Each arm is a single assignment; nothing nests. Splitting the
    // switch in two -- "flags that take a value" and "flags that do not" -- would halve the number while
    // making the parser worse, because checking that a flag is handled exactly once would then mean
    // reading both halves and trusting they do not overlap. The threshold in config/pmd/ruleset.xml stays
    // at 20 so a method whose *logic* branches that widely is still reported; the exception is granted
    // here, at the point of use, so that it cannot extend to any other method.
    @SuppressWarnings("PMD.CyclomaticComplexity")
    public static CliOptions parse(String[] args) {
        Path input = null;
        Path output = null;
        String pipeline = "";
        String format = null;
        Float quality = null;
        int parallelism = 0;
        boolean recursive = false;
        boolean overwrite = false;
        boolean strip = false;
        boolean history = true;
        boolean quiet = false;
        boolean dryRun = false;
        boolean help = false;
        boolean version = false;

        // `i` is the parse cursor, not just a counter. A flag that takes a value reads args[i + 1]
        // and advances past it, which is what the `i++` in those cases does: post-increment hands
        // value() the index of the *flag*, and the loop's own i++ then lands on the next flag. Every
        // value-taking case is written the same way so the pattern is checkable by eye.
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--in", "-i" -> input = path(value(args, i++, arg), arg);
                case "--out", "-o" -> output = path(value(args, i++, arg), arg);
                case "--pipeline", "-p" -> pipeline = value(args, i++, arg);
                case "--format", "-f" -> format = format(value(args, i++, arg));
                case "--quality" -> quality = quality(value(args, i++, arg));
                case "--parallelism" -> parallelism = positiveInt(value(args, i++, arg), arg);
                case "--recursive", "-r" -> recursive = true;
                case "--overwrite" -> overwrite = true;
                case "--strip-metadata" -> strip = true;
                case "--no-history" -> history = false;
                case "--quiet", "-q" -> quiet = true;
                case "--dry-run" -> dryRun = true;
                case "--help", "-h" -> help = true;
                case "--version", "-V" -> version = true;
                // "--ui" and "--serve" are consumed by Main before it gets here; anything else left is a
                // typo, and a typo must not be ignored. A silently dropped "--overwite" is a batch that
                // skips every file and an operator who believes the flag worked.
                default -> throw new CliSyntaxException(unknown(arg));
            }
        }

        return new CliOptions(
                Optional.ofNullable(input),
                Optional.ofNullable(output),
                pipeline,
                Optional.ofNullable(format),
                Optional.ofNullable(quality),
                parallelism,
                recursive,
                overwrite,
                strip,
                history,
                quiet,
                dryRun,
                help,
                version);
    }

    /**
     * Reads the argument that follows a flag.
     *
     * <p>Rejects a following token that starts with {@code -}. {@code --in --out /tmp/x} would otherwise
     * bind {@code "--out"} as the input directory and then complain that {@code --out} is missing, which
     * points the operator at the wrong half of the line.
     */
    private static String value(String[] args, int index, String flag) {
        if (index + 1 >= args.length) {
            throw new CliSyntaxException(flag + " requires a value");
        }
        String next = args[index + 1];
        if (next.startsWith("-") && next.length() > 1) {
            throw new CliSyntaxException(flag + " requires a value, but the next token is " + next);
        }
        return next;
    }

    private static Path path(String raw, String flag) {
        try {
            return Path.of(raw).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            // Common on Windows: a quoted path with a trailing backslash swallows the closing quote, so
            // argv contains something like `C:\photos" --out`. Naming the flag makes that obvious.
            throw new CliSyntaxException(flag + ": not a valid path: " + raw, e);
        }
    }

    private static String format(String raw) {
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        if (!FORMATS.contains(lower)) {
            throw new CliSyntaxException("unsupported --format " + raw + "; expected one of " + FORMATS);
        }
        return lower;
    }

    private static float quality(String raw) {
        float parsed;
        try {
            parsed = Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            throw new CliSyntaxException(
                    "--quality must be a number between 0.0 and 1.0, not " + raw, e);
        }
        // Negated in-range rather than "below 0 or above 1", which is false for NaN -- and
        // Float.parseFloat("NaN") succeeds. NaN would then reach ProcessingOptions, whose preconditions
        // throw a plain IllegalArgumentException: exit 3 (startup failure) for what is a usage error.
        if (!(parsed >= 0.0f && parsed <= 1.0f)) {
            // Caught here rather than in validate() because "85" is the single most likely mistake -- JPEG
            // quality is conventionally 0-100 everywhere else -- and the fix belongs in the message.
            throw new CliSyntaxException("--quality must be between 0.0 and 1.0 (got " + raw
                    + "; for 85% pass 0.85)");
        }
        return parsed;
    }

    private static int positiveInt(String raw, String flag) {
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < 0) {
                throw new CliSyntaxException(flag + " cannot be negative: " + raw);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new CliSyntaxException(flag + " must be an integer, not " + raw, e);
        }
    }

    /** Suggests the long form when a plausible typo of one was given. */
    private static String unknown(String arg) {
        String hint = switch (arg) {
            case "--input", "--source", "--src" -> " (did you mean --in?)";
            case "--output", "--dest", "--target" -> " (did you mean --out?)";
            case "--ops", "--operations" -> " (did you mean --pipeline?)";
            case "--threads", "--jobs", "-j" -> " (did you mean --parallelism?)";
            case "--force" -> " (did you mean --overwrite?)";
            default -> "";
        };
        return "unknown option " + arg + hint + "; run with --help";
    }

    // ------------------------------------------------------------------------
    //  Validation
    // ------------------------------------------------------------------------

    /**
     * Every semantic problem with these options, as messages fit to print.
     *
     * <p>A list, not an exception: the operator sees all of them in one run. Empty means runnable.
     *
     * <p>The pipeline is parsed here for its side effect of failing — {@link PipelineFormat} owns the
     * grammar and is the only thing that can judge {@code "resize:1920"}, and duplicating even part of
     * that check would guarantee the two drifted apart.
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();

        if (input.isEmpty()) {
            problems.add("--in is required");
        }
        if (output.isEmpty()) {
            problems.add("--out is required");
        }
        if (input.isPresent() && input.equals(output)) {
            // Not merely unwise: with --overwrite it rewrites each source in place through a lossy
            // encoder, and without it every job is skipped. Neither is what anyone meant.
            problems.add("--in and --out must differ (both are " + input.get() + ")");
        }
        try {
            PipelineFormat.parse(pipelineSpec);
        } catch (IllegalArgumentException e) {
            problems.add("--pipeline: " + e.getMessage());
        }
        if (quality.isPresent() && format.isPresent() && !isLossy(format.get())) {
            // A warning rather than an error would be ignored; this is cheap to fix and silently
            // ineffective otherwise, which is the worst combination.
            problems.add("--quality has no effect on " + format.get() + " (lossless format)");
        }
        return List.copyOf(problems);
    }

    private static boolean isLossy(String format) {
        return format.equals("jpg") || format.equals("jpeg") || format.equals("webp");
    }

    // ------------------------------------------------------------------------
    //  Projection onto the core model
    // ------------------------------------------------------------------------

    /**
     * Layers these options over the configured defaults.
     *
     * <p>{@code defaults} is {@code AppConfig}'s, not {@link ProcessingOptions#defaults()}: a value set
     * in {@code config/application.properties} must survive a CLI invocation that does not mention it.
     * Only flags actually present on the command line override, which is why the optional fields are
     * {@link Optional} and not sentinel values — {@code null} format and {@code -1} quality would both
     * have to be special-cased at every read.
     */
    public ProcessingOptions toProcessingOptions(ProcessingOptions defaults) {
        List<ImageOperation> operations = pipelineSpec.isBlank()
                ? defaults.operations()
                : PipelineFormat.parse(pipelineSpec);

        return ProcessingOptions.builder()
                .operations(operations)
                .outputFormat(format.orElse(defaults.outputFormat()))
                .quality(quality.orElse(defaults.quality()))
                .tileThresholdPixels(defaults.tileThresholdPixels())
                .batchThresholdJobs(defaults.batchThresholdJobs())
                // Boolean flags can only turn things on, so an absent flag inherits the default. That is
                // why there is no --no-overwrite: the properties file is the place to switch it back off.
                .stripMetadata(stripMetadata || defaults.stripMetadata())
                .overwriteExisting(overwrite || defaults.overwriteExisting())
                .maxPixelsPerImage(defaults.maxPixelsPerImage())
                .build();
    }

    /**
     * Usage text for {@link Locale#ROOT} (English). One call so {@code --help} and the error path
     * cannot disagree.
     *
     * <p>ASCII only, enforced by a test. {@code System.out} on Windows encodes with the console codepage
     * rather than {@code file.encoding}, so an en dash in "0.0-1.0"
     * would print as {@code ?} in {@code cmd.exe} — and help text that renders as garbage is worse than
     * help text that is plain. That constraint is specific to this, the default bundle entry; a
     * translation loaded through {@link #usage(Locale)} is not held to it.
     */
    public static String usage() {
        return usage(Locale.ROOT);
    }

    /** Usage text translated for {@code locale}, falling back to {@link Locale#ROOT} when untranslated. */
    public static String usage(Locale locale) {
        return new Messages(locale).get("cli.usage");
    }
}
