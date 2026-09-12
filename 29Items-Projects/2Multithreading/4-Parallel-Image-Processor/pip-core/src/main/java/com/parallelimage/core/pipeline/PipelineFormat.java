package com.parallelimage.core.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The compact textual form of an operation chain: {@code grayscale>resize:400x400>sharpen:1.2}.
 *
 * <h2>Why this exists in core rather than in the CLI</h2>
 * Three places need it and they are in three different modules: {@code CliRunner} parses it from
 * {@code --pipeline}, {@code SqliteJobRepository} writes it into {@code batches.pipeline} so that "why
 * is this output 4000px wide" has an answer, and the UI reads it back out of {@code pipeline_presets}.
 * Two implementations of a serialisation format eventually disagree — usually about a corner nobody
 * tested, like whether {@code resize:400x400} preserves aspect ratio — so there is one, here, next to
 * the sealed interface it serialises. {@link #parse} and {@link #render} are tested as a round trip.
 *
 * <h2>What the format deliberately does not cover</h2>
 * {@link ImageOperation.Watermark} renders as the bare word {@code watermark} and does <em>not</em>
 * parse. Its parameters include free text and a filesystem path, either of which may contain the
 * {@code >} and {@code :} this format uses as delimiters, and inventing an escaping scheme for a
 * history column would be effort spent on the wrong problem — watermarks are configured from the
 * {@code watermark_presets} table, which stores each field in its own column and needs no quoting.
 * Rendering it as a word keeps the history line honest ("a watermark was applied") without pretending
 * the string is a complete description. {@link #parse} says so explicitly rather than failing with a
 * generic "unknown operation".
 */
public final class PipelineFormat {

    /** Separates stages. */
    private static final char STAGE = '>';

    /** Separates a stage name from its arguments. */
    private static final char ARG = ':';

    private PipelineFormat() {
    }

    /**
     * Renders a chain to its textual form. An empty chain renders as the empty string, which is what
     * {@code batches.pipeline} stores for a pure format conversion.
     */
    public static String render(List<ImageOperation> operations) {
        StringBuilder out = new StringBuilder(64);
        for (ImageOperation operation : operations) {
            if (out.length() > 0) {
                out.append(STAGE);
            }
            out.append(renderOne(operation));
        }
        return out.toString();
    }

    /** Exhaustive over the sealed interface: a new operation type will not compile until handled. */
    private static String renderOne(ImageOperation operation) {
        return switch (operation) {
            case ImageOperation.Grayscale g -> "grayscale";
            case ImageOperation.Resize r -> "resize" + ARG + r.targetWidth() + "x" + r.targetHeight()
                    + (r.preserveAspectRatio() ? ARG + "fit" : "");
            case ImageOperation.BoxBlur b -> "blur" + ARG + b.radius();
            case ImageOperation.Sharpen s -> "sharpen" + ARG + trim(s.amount());
            case ImageOperation.Enhance e -> "enhance" + ARG
                    + e.mode().name().toLowerCase(Locale.ROOT) + ARG + trim(e.strength());
            // Lossy on purpose — see the class javadoc.
            case ImageOperation.Watermark w -> "watermark";
        };
    }

    /**
     * Parses the textual form.
     *
     * @param text a chain, or {@code null}/blank for an empty chain
     * @throws IllegalArgumentException if a stage is unknown or its arguments do not parse. The
     *     message names the offending stage: this text usually comes from a user typing
     *     {@code --pipeline}, and "unknown operation 'greyscale'" is the whole value of the error.
     */
    public static List<ImageOperation> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<ImageOperation> operations = new ArrayList<>();
        for (String stage : text.split(String.valueOf(STAGE))) {
            String trimmed = stage.trim();
            if (!trimmed.isEmpty()) {
                operations.add(parseOne(trimmed));
            }
        }
        return List.copyOf(operations);
    }

    private static ImageOperation parseOne(String stage) {
        String[] parts = stage.split(String.valueOf(ARG), -1);
        String name = parts[0].trim().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "grayscale", "greyscale" -> new ImageOperation.Grayscale();
            case "blur" -> new ImageOperation.BoxBlur(requireInteger(stage, parts, 1));
            case "sharpen" -> new ImageOperation.Sharpen(requireNumber(stage, parts, 1));
            case "resize" -> parseResize(stage, parts);
            case "enhance" -> parseEnhance(stage, parts);
            case "watermark" -> throw new IllegalArgumentException(
                    "watermark cannot be written as pipeline text because its text and overlay path"
                            + " may contain the '>' and ':' delimiters; select a row from"
                            + " watermark_presets instead");
            default -> throw new IllegalArgumentException("unknown operation '" + name + "' in '"
                    + stage + "'; expected one of grayscale, resize, blur, sharpen, enhance");
        };
    }

    private static ImageOperation parseResize(String stage, String[] parts) {
        if (parts.length < 2) {
            throw new IllegalArgumentException(
                    "resize needs dimensions, e.g. resize:400x400 or resize:400x400:fit, got '"
                            + stage + "'");
        }
        String[] dimensions = parts[1].trim().toLowerCase(Locale.ROOT).split("x", -1);
        if (dimensions.length != 2) {
            throw new IllegalArgumentException(
                    "resize dimensions must be <width>x<height>, got '" + parts[1] + "'");
        }
        boolean fit = parts.length > 2 && "fit".equalsIgnoreCase(parts[2].trim());
        try {
            return new ImageOperation.Resize(Integer.parseInt(dimensions[0].trim()),
                    Integer.parseInt(dimensions[1].trim()), fit);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "resize dimensions must be integers, got '" + parts[1] + "'", e);
        }
    }

    private static ImageOperation parseEnhance(String stage, String[] parts) {
        if (parts.length < 2) {
            throw new IllegalArgumentException("enhance needs a mode, e.g. enhance:clahe, got '"
                    + stage + "'");
        }
        String mode = parts[1].trim().toUpperCase(Locale.ROOT);
        ImageOperation.EnhanceMode parsed;
        try {
            parsed = ImageOperation.EnhanceMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown enhance mode '" + parts[1].trim()
                    + "'; expected clahe, denoise or super_resolution", e);
        }
        // Strength is optional: enhance:clahe means "the algorithm's own default intensity".
        double strength = parts.length > 2 && !parts[2].isBlank() ? requireNumber(stage, parts, 2) : 0.5d;
        return new ImageOperation.Enhance(parsed, strength);
    }

    /**
     * Reads an argument that must be a whole number.
     *
     * <p>Separate from {@link #requireNumber} because {@code (int) requireNumber(...)} truncates:
     * {@code blur:2.5} would become a radius of 2 and produce a visibly different image from the one
     * asked for, with nothing said about it. The blur radius is documented as an integer in
     * {@code --help}, and a value the format cannot represent is refused rather than rounded — the same
     * rule the strict boolean readers follow.
     */
    private static int requireInteger(String stage, String[] parts, int index) {
        double value = requireNumber(stage, parts, index);
        if (value != Math.rint(value)) {
            throw new IllegalArgumentException("'" + parts[index].trim() + "' in '" + stage
                    + "' must be a whole number");
        }
        return (int) value;
    }

    private static double requireNumber(String stage, String[] parts, int index) {
        if (parts.length <= index || parts[index].isBlank()) {
            throw new IllegalArgumentException(
                    "'" + stage + "' is missing its numeric argument at position " + index);
        }
        try {
            return Double.parseDouble(parts[index].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "'" + parts[index].trim() + "' in '" + stage + "' is not a number", e);
        }
    }

    /** {@code 1.0 -> "1"}, {@code 1.2 -> "1.2"} — keeps the stored text as short as the user typed. */
    private static String trim(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
