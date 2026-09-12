package com.example.pipeline.presentation.cli;

import com.example.pipeline.infrastructure.config.ConfigLoader;
import com.example.pipeline.infrastructure.config.ConfigurationException;
import com.example.pipeline.infrastructure.config.PipelineConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parses {@code --key=value} command-line flags into configuration overrides.
 *
 * <p><strong>Why hand-rolled rather than picard/JCommander/commons-cli.</strong> The whole
 * grammar is three productions — {@code --help}, {@code --key=value}, and everything else
 * is an error — and the keys are already defined by {@link PipelineConfig}. A dependency
 * would add a jar and a CVE feed to parse a format that fits in one method, and the flag
 * names would then live in annotations instead of next to the settings they set.
 *
 * <p><strong>Unknown flags are rejected, not ignored.</strong> A silently ignored
 * {@code --pipeline.consumer.thread=8} (note the missing "s") is a run that quietly used
 * the default and a benchmark number that means nothing. Rejecting costs one line and
 * turns a wrong answer into an error message. The same rule applies inside
 * {@link ConfigLoader} to both property-file layers, so a typo is caught wherever it was
 * written; environment variables are read by known name only, so an unrecognised
 * {@code PIPELINE_*} variable is never picked up in the first place.
 *
 * <p>The flag name is the property key verbatim — {@code --pipeline.batch.size=256} — so
 * there is exactly one vocabulary across CLI flags, {@code application.properties},
 * profile files and {@code PIPELINE_*} environment variables. No mapping table to keep in
 * sync, and a log line naming a key is directly actionable whichever source set it.
 *
 * <p>Immutable; construct with {@link #parse(String[])}.
 */
public final class CliArguments {

    /** Flag requesting the usage text. */
    public static final String HELP_FLAG = "--help";

    /** Prefix every recognised flag carries. */
    public static final String FLAG_PREFIX = "--";

    private final Map<String, String> overrides;
    private final boolean helpRequested;

    private CliArguments(Map<String, String> overrides, boolean helpRequested) {
        // A LinkedHashMap behind an unmodifiable view rather than Map.copyOf: copyOf leaves
        // iteration order unspecified, which would make the startup log line -- and any diff
        // of two runs' logs -- reorder for no reason. Order is command-line order, so
        // "which flags did this run get" reads back exactly as it was typed. The backing map
        // is never leaked, so the view is effectively immutable.
        this.overrides = Collections.unmodifiableMap(new LinkedHashMap<>(overrides));
        this.helpRequested = helpRequested;
    }

    /**
     * Parses the raw argument array.
     *
     * @param args arguments as handed to {@code main}
     * @return the parsed overrides
     * @throws ConfigurationException if a flag is malformed, unknown or repeated
     */
    public static CliArguments parse(String[] args) {
        Objects.requireNonNull(args, "args");
        Map<String, String> overrides = new LinkedHashMap<>();
        boolean help = false;
        for (String raw : args) {
            String arg = raw.strip();
            if (arg.isEmpty()) {
                continue;
            }
            if (HELP_FLAG.equals(arg) || "-h".equals(arg)) {
                help = true;
                continue;
            }
            if (!arg.startsWith(FLAG_PREFIX)) {
                throw new ConfigurationException(arg, "is not a recognised argument; expected --key=value");
            }
            String body = arg.substring(FLAG_PREFIX.length());
            int equals = body.indexOf('=');
            if (equals <= 0) {
                throw new ConfigurationException(arg, "must be of the form --key=value");
            }
            String key = body.substring(0, equals).strip();
            String value = body.substring(equals + 1).strip();
            if (!ConfigLoader.knownKeys().contains(key)) {
                throw new ConfigurationException(key, "is not a known setting; run with --help for the list");
            }
            // Repeats are an error rather than last-one-wins: two contradictory flags in a
            // long script line are a mistake, and picking one silently hides it.
            if (overrides.put(key, value) != null) {
                throw new ConfigurationException(key, "was given more than once");
            }
        }
        return new CliArguments(overrides, help);
    }

    /** Overrides in the order they appeared; keys are validated property names. */
    public Map<String, String> overrides() {
        return overrides;
    }

    /** Whether {@code --help} (or {@code -h}) was present. */
    public boolean helpRequested() {
        return helpRequested;
    }

    /** Whether any override was supplied. */
    public boolean isEmpty() {
        return overrides.isEmpty();
    }

    /**
     * The usage text, listing every configurable key.
     *
     * @return multi-line, ASCII-only help text
     */
    public static String usage() {
        StringBuilder out = new StringBuilder(2048);
        out.append("Event Processing Pipeline\n\n")
                .append("Usage:\n")
                .append("  java -jar event-processing-pipeline.jar [--key=value ...]\n\n")
                .append("Configuration precedence (lowest to highest):\n")
                .append("  built-in defaults < classpath application.properties\n")
                .append("    < config/application-<env>.properties < PIPELINE_* env vars < --key=value\n\n")
                .append("Every key below is also readable as an environment variable, for example\n")
                .append("  ").append(PipelineConfig.KEY_BATCH_SIZE).append(" -> ")
                .append(ConfigLoader.envVarFor(PipelineConfig.KEY_BATCH_SIZE)).append("\n\n")
                .append("Settings:\n");
        List<String> keys = List.copyOf(ConfigLoader.knownKeys());
        for (String key : keys) {
            out.append("  --").append(key).append("=<value>\n");
        }
        out.append("\nOther flags:\n")
                .append("  --help, -h    print this text and exit 0\n\n")
                .append("Exit codes:\n")
                .append("  0   run completed and the counters reconcile\n")
                .append("  1   run failed, or events were lost\n")
                .append("  2   invalid configuration (nothing was started)\n")
                .append("  130 interrupted (Ctrl+C) after a clean drain\n");
        return out.toString();
    }

    @Override
    public String toString() {
        return "CliArguments" + overrides + (helpRequested ? "+help" : "");
    }
}
