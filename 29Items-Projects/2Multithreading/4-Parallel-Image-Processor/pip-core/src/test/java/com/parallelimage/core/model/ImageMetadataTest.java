package com.parallelimage.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link ImageMetadata} tests. */
class ImageMetadataTest {

    private static ImageMetadata metadata(int width, int height) {
        return new ImageMetadata("job-1", width, height, "png", 1_000L, 2, false, Map.of());
    }

    @Nested
    @DisplayName("canonical constructor validation")
    class Validation {

        @Test
        @DisplayName("rejects a blank jobId")
        void rejectsBlankJobId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ImageMetadata(" ", 10, 10, "png", 0L, 2, false, Map.of()));
        }

        @Test
        @DisplayName("rejects a zero width")
        void rejectsZeroWidth() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ImageMetadata("job-1", 0, 10, "png", 0L, 2, false, Map.of()));
        }

        @Test
        @DisplayName("rejects a negative width")
        void rejectsNegativeWidth() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ImageMetadata("job-1", -1, 10, "png", 0L, 2, false, Map.of()));
        }

        @Test
        @DisplayName("rejects a zero height")
        void rejectsZeroHeight() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ImageMetadata("job-1", 10, 0, "png", 0L, 2, false, Map.of()));
        }

        @Test
        @DisplayName("rejects a negative height")
        void rejectsNegativeHeight() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ImageMetadata("job-1", 10, -1, "png", 0L, 2, false, Map.of()));
        }

        @Test
        @DisplayName("rejects a blank formatName")
        void rejectsBlankFormatName() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ImageMetadata("job-1", 10, 10, " ", 0L, 2, false, Map.of()));
        }

        @Test
        @DisplayName("rejects negative sourceBytes")
        void rejectsNegativeSourceBytes() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ImageMetadata("job-1", 10, 10, "png", -1L, 2, false, Map.of()));
        }

        @Test
        @DisplayName("accepts zero sourceBytes")
        void acceptsZeroSourceBytes() {
            ImageMetadata metadata = new ImageMetadata("job-1", 10, 10, "png", 0L, 2, false, Map.of());
            assertEquals(0L, metadata.sourceBytes());
        }

        @Test
        @DisplayName("defaults a null exif map to an empty map instead of throwing")
        void nullExifBecomesEmptyMap() {
            ImageMetadata metadata = new ImageMetadata("job-1", 10, 10, "png", 0L, 2, false, null);
            assertEquals(Map.of(), metadata.exif());
        }

        @Test
        @DisplayName("defensively copies a mutable exif map so later mutation is not visible")
        void exifMapIsDefensivelyCopied() {
            Map<String, String> mutable = new HashMap<>();
            mutable.put("Make", "Canon");
            ImageMetadata metadata = new ImageMetadata("job-1", 10, 10, "png", 0L, 2, false, mutable);
            mutable.put("Model", "EOS");
            assertEquals(Map.of("Make", "Canon"), metadata.exif());
        }
    }

    @Nested
    @DisplayName("pixelCount() and derived metrics")
    class DerivedMetrics {

        @Test
        @DisplayName("pixelCount() multiplies width by height")
        void pixelCountMultipliesDimensions() {
            assertEquals(100L, metadata(10, 10).pixelCount());
        }

        @Test
        @DisplayName("pixelCount() does not overflow int for very large dimensions")
        void pixelCountUsesLongArithmetic() {
            ImageMetadata large = metadata(50_000, 50_000);
            assertEquals(2_500_000_000L, large.pixelCount());
        }

        @Test
        @DisplayName("estimatedHeapBytes() is four bytes per pixel")
        void estimatedHeapBytesIsFourBytesPerPixel() {
            assertEquals(400L, metadata(10, 10).estimatedHeapBytes());
        }

        @Test
        @DisplayName("estimatedHeapBytes() scales with pixel count")
        void estimatedHeapBytesScales() {
            assertEquals(40_000L, metadata(100, 100).estimatedHeapBytes());
        }

        @Test
        @DisplayName("megapixels() divides pixel count by one million")
        void megapixelsDividesByOneMillion() {
            assertEquals(1.0d, metadata(1_000, 1_000).megapixels(), 1e-9);
        }

        @Test
        @DisplayName("megapixels() reflects a fractional pixel count")
        void megapixelsIsFractionalForSmallImages() {
            assertEquals(0.0001d, metadata(10, 10).megapixels(), 1e-9);
        }
    }

    @Nested
    @DisplayName("warrantsTiling()")
    class WarrantsTiling {

        @Test
        @DisplayName("is true when pixel count exceeds the threshold")
        void trueWhenAboveThreshold() {
            assertTrue(metadata(10, 10).warrantsTiling(99L));
        }

        @Test
        @DisplayName("is false when pixel count equals the threshold (strict greater-than)")
        void falseWhenEqualToThreshold() {
            assertFalse(metadata(10, 10).warrantsTiling(100L));
        }

        @Test
        @DisplayName("is false when pixel count is below the threshold")
        void falseWhenBelowThreshold() {
            assertFalse(metadata(10, 10).warrantsTiling(101L));
        }
    }
}
