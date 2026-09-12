package com.parallelimage.core.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link PipelineFormat} tests, weighted towards the round trip.
 *
 * <p>The format has exactly one job: what {@code SqliteJobRepository} writes into
 * {@code batches.pipeline} must be what {@code --pipeline} would parse back. A format that is merely
 * "close enough" in both directions is how a stored preset silently stops preserving aspect ratio, so
 * the round trip is asserted on structure ({@code equals} over the records), not on text.
 */
class PipelineFormatTest {

    @Test
    @DisplayName("an empty chain is the empty string, both ways")
    void emptyChain() {
        assertEquals("", PipelineFormat.render(List.of()));
        assertEquals(List.of(), PipelineFormat.parse(""));
        assertEquals(List.of(), PipelineFormat.parse(null));
        assertEquals(List.of(), PipelineFormat.parse("   "));
    }

    @Test
    @DisplayName("every representable operation survives render then parse")
    void roundTrip() {
        List<ImageOperation> original = List.of(
                new ImageOperation.Grayscale(),
                new ImageOperation.Resize(400, 300, false),
                new ImageOperation.Resize(800, 800, true),
                new ImageOperation.BoxBlur(3),
                new ImageOperation.Sharpen(1.2d),
                new ImageOperation.Enhance(ImageOperation.EnhanceMode.CLAHE, 0.75d),
                new ImageOperation.Enhance(ImageOperation.EnhanceMode.SUPER_RESOLUTION, 1.0d));

        String text = PipelineFormat.render(original);
        assertEquals(original, PipelineFormat.parse(text), "round trip lost or changed a stage: " + text);
    }

    @Test
    @DisplayName("the rendered text is the documented syntax, not just something that parses")
    void renderedTextIsStable() {
        // Pinned because this string ends up in a database column and in --pipeline arguments users
        // copy out of the history screen. Changing it is a compatibility decision, not a refactor.
        assertEquals("grayscale>resize:400x400>resize:800x600:fit>blur:2>sharpen:1.5>enhance:denoise:0.4",
                PipelineFormat.render(List.of(
                        new ImageOperation.Grayscale(),
                        new ImageOperation.Resize(400, 400, false),
                        new ImageOperation.Resize(800, 600, true),
                        new ImageOperation.BoxBlur(2),
                        new ImageOperation.Sharpen(1.5d),
                        new ImageOperation.Enhance(ImageOperation.EnhanceMode.DENOISE, 0.4d))));
    }

    @Test
    @DisplayName("whole numbers render without a trailing .0")
    void wholeNumbersAreTerse() {
        assertEquals("sharpen:2", PipelineFormat.render(List.of(new ImageOperation.Sharpen(2.0d))));
        assertEquals(List.of(new ImageOperation.Sharpen(2.0d)), PipelineFormat.parse("sharpen:2"));
    }

    @Test
    @DisplayName("resize without :fit does not preserve aspect ratio, and with it does")
    void fitFlagIsNotLost() {
        assertEquals(List.of(new ImageOperation.Resize(400, 400, false)),
                PipelineFormat.parse("resize:400x400"));
        assertEquals(List.of(new ImageOperation.Resize(400, 400, true)),
                PipelineFormat.parse("resize:400x400:fit"));
    }

    @Test
    @DisplayName("whitespace and the British spelling are tolerated")
    void toleratesHumanInput() {
        assertEquals(
                List.of(new ImageOperation.Grayscale(), new ImageOperation.BoxBlur(4)),
                PipelineFormat.parse(" greyscale > blur:4 "));
    }

    @Test
    @DisplayName("an empty stage between separators is skipped rather than rejected")
    void skipsEmptyStages() {
        assertEquals(List.of(new ImageOperation.Grayscale()), PipelineFormat.parse(">grayscale>"));
    }

    @Test
    @DisplayName("enhance defaults its strength when only a mode is given")
    void enhanceStrengthIsOptional() {
        assertEquals(List.of(new ImageOperation.Enhance(ImageOperation.EnhanceMode.CLAHE, 0.5d)),
                PipelineFormat.parse("enhance:clahe"));
    }

    @ParameterizedTest
    @DisplayName("a malformed stage names itself in the message")
    @ValueSource(strings = {
        "greyscale>bogus",          // unknown stage
        "resize",                   // missing dimensions
        "resize:400",               // not WxH
        "resize:fourhundredx400",   // non-numeric dimensions
        "blur",                     // missing numeric argument
        "blur:thick",               // non-numeric argument
        "blur:2.5",                 // fractional radius: refused, never truncated to 2
        "blur:0",                   // below the radius range
        "blur:65",                  // above the radius range
        "enhance",                  // missing mode
        "enhance:magic",            // unknown mode
    })
    void malformedStagesAreRejected(String text) {
        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> PipelineFormat.parse(text));
        assertTrue(thrown.getMessage() != null && !thrown.getMessage().isBlank(),
                "the message is the entire value of this error to a user typing --pipeline");
    }

    @Test
    @DisplayName("watermark renders as a word and refuses to parse, and says why")
    void watermarkIsDeliberatelyLossy() {
        // The asymmetry is documented behaviour: the delimiters can appear inside a watermark's text
        // or overlay path, so watermarks come from watermark_presets rather than from this string.
        assertEquals("watermark",
                PipelineFormat.render(List.of(ImageOperation.Watermark.ofText("(c) Studio"))));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> PipelineFormat.parse("watermark"));
        assertTrue(thrown.getMessage().contains("watermark_presets"),
                "the error must point at the supported alternative, got: " + thrown.getMessage());
    }
}
