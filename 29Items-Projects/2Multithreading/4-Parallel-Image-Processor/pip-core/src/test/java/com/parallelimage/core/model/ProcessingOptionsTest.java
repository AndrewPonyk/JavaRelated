package com.parallelimage.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.pipeline.ImageOperation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** {@link ProcessingOptions} tests. */
class ProcessingOptionsTest {

    private static ProcessingOptions.Builder validBuilder() {
        return ProcessingOptions.builder()
                .outputFormat("png")
                .quality(0.9f)
                .tileThresholdPixels(1_024L)
                .batchThresholdJobs(4)
                .maxPixelsPerImage(1_000L);
    }

    @Nested
    @DisplayName("canonical constructor validation")
    class Validation {

        @Test
        @DisplayName("rejects null operations")
        void rejectsNullOperations() {
            assertThrows(NullPointerException.class,
                    () -> new ProcessingOptions(null, "png", 0.9f, 1_024L, 4, true, false, 1_000L));
        }

        @Test
        @DisplayName("rejects a null outputFormat")
        void rejectsNullOutputFormat() {
            assertThrows(NullPointerException.class,
                    () -> new ProcessingOptions(List.of(), null, 0.9f, 1_024L, 4, true, false, 1_000L));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("rejects a blank outputFormat")
        void rejectsBlankOutputFormat(String blank) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ProcessingOptions(List.of(), blank, 0.9f, 1_024L, 4, true, false, 1_000L));
        }

        @ParameterizedTest
        @ValueSource(floats = {-0.1f, 1.1f})
        @DisplayName("rejects a quality outside [0,1]")
        void rejectsOutOfRangeQuality(float quality) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ProcessingOptions(List.of(), "png", quality, 1_024L, 4, true, false, 1_000L));
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("rejects a non-positive tileThresholdPixels")
        void rejectsNonPositiveTileThreshold(long threshold) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ProcessingOptions(List.of(), "png", 0.9f, threshold, 4, true, false, 1_000L));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        @DisplayName("rejects a non-positive batchThresholdJobs")
        void rejectsNonPositiveBatchThreshold(int jobs) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ProcessingOptions(List.of(), "png", 0.9f, 1_024L, jobs, true, false, 1_000L));
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("rejects a non-positive maxPixelsPerImage")
        void rejectsNonPositiveMaxPixels(long maxPixels) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ProcessingOptions(List.of(), "png", 0.9f, 1_024L, 4, true, false, maxPixels));
        }

        @Test
        @DisplayName("accepts quality at the boundaries")
        void acceptsQualityAtBoundaries() {
            new ProcessingOptions(List.of(), "png", 0.0f, 1_024L, 4, true, false, 1_000L);
            new ProcessingOptions(List.of(), "png", 1.0f, 1_024L, 4, true, false, 1_000L);
        }

        @Test
        @DisplayName("defensively copies the operations list")
        void copiesOperationsList() {
            List<ImageOperation> mutable = new java.util.ArrayList<>();
            ProcessingOptions options =
                    new ProcessingOptions(mutable, "png", 0.9f, 1_024L, 4, true, false, 1_000L);
            mutable.clear();
            assertTrue(options.operations().isEmpty());
        }

        @Test
        @DisplayName("normalizes outputFormat to lowercase and jpeg to jpg")
        void normalizesOutputFormat() {
            ProcessingOptions options =
                    new ProcessingOptions(List.of(), "JPEG", 0.9f, 1_024L, 4, true, false, 1_000L);
            assertEquals("jpg", options.outputFormat());
        }
    }

    @Nested
    @DisplayName("builder")
    class BuilderRoundTrip {

        @Test
        @DisplayName("defaults() produces valid, privacy-preserving options")
        void defaultsAreValid() {
            ProcessingOptions options = ProcessingOptions.defaults();
            assertEquals("png", options.outputFormat());
            assertTrue(options.operations().isEmpty());
            assertTrue(options.stripMetadata());
        }

        @Test
        @DisplayName("round-trips every field through the builder")
        void roundTripsAllFields() {
            ImageOperation grayscale = new ImageOperation.Grayscale();
            ProcessingOptions options = ProcessingOptions.builder()
                    .operations(grayscale)
                    .outputFormat("webp")
                    .quality(0.75f)
                    .tileThresholdPixels(2_048L)
                    .batchThresholdJobs(6)
                    .stripMetadata(false)
                    .overwriteExisting(true)
                    .maxPixelsPerImage(5_000L)
                    .build();

            assertEquals(List.of(grayscale), options.operations());
            assertEquals("webp", options.outputFormat());
            assertEquals(0.75f, options.quality());
            assertEquals(2_048L, options.tileThresholdPixels());
            assertEquals(6, options.batchThresholdJobs());
            assertEquals(false, options.stripMetadata());
            assertEquals(true, options.overwriteExisting());
            assertEquals(5_000L, options.maxPixelsPerImage());
        }

        @Test
        @DisplayName("withOperations() replaces only the operations list")
        void withOperationsReplacesOnlyThatField() {
            ProcessingOptions original = validBuilder().build();
            ImageOperation grayscale = new ImageOperation.Grayscale();

            ProcessingOptions updated = original.withOperations(List.of(grayscale));

            assertEquals(List.of(grayscale), updated.operations());
            assertEquals(original.outputFormat(), updated.outputFormat());
            assertEquals(original.quality(), updated.quality());
        }
    }
}
