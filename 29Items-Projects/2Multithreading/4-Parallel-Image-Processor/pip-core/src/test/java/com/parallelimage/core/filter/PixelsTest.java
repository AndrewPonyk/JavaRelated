package com.parallelimage.core.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.error.PipelineException;
import java.awt.Point;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PixelsTest {

    @Nested
    @DisplayName("isIntPacked")
    class IsIntPacked {

        @Test
        @DisplayName("true for TYPE_INT_RGB and TYPE_INT_ARGB")
        void trueForPackedTypes() {
            assertTrue(Pixels.isIntPacked(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)));
            assertTrue(Pixels.isIntPacked(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)));
        }

        @Test
        @DisplayName("false for a byte-backed type")
        void falseForByteBacked() {
            assertFalse(Pixels.isIntPacked(new BufferedImage(2, 2, BufferedImage.TYPE_3BYTE_BGR)));
        }
    }

    @Nested
    @DisplayName("normalize")
    class Normalize {

        @Test
        @DisplayName("an already int-packed image is returned unchanged")
        void alreadyPackedIsUnchanged() {
            BufferedImage source = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
            assertSame(source, Pixels.normalize(source));
        }

        @Test
        @DisplayName("a non-packed image with alpha converts to TYPE_INT_ARGB")
        void alphaSourceConvertsToIntArgb() {
            BufferedImage source = new BufferedImage(3, 3, BufferedImage.TYPE_4BYTE_ABGR);
            assertEquals(BufferedImage.TYPE_INT_ARGB, Pixels.normalize(source).getType());
        }

        @Test
        @DisplayName("a non-packed image without alpha converts to TYPE_INT_RGB")
        void opaqueSourceConvertsToIntRgb() {
            BufferedImage source = new BufferedImage(3, 3, BufferedImage.TYPE_3BYTE_BGR);
            assertEquals(BufferedImage.TYPE_INT_RGB, Pixels.normalize(source).getType());
        }

        @Test
        @DisplayName("pixel content survives the conversion")
        void pixelContentIsPreserved() {
            BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_3BYTE_BGR);
            source.setRGB(1, 0, 0x11_2233);
            BufferedImage result = Pixels.normalize(source);
            assertEquals(0xFF_112233, result.getRGB(1, 0));
        }
    }

    @Nested
    @DisplayName("sameShape")
    class SameShape {

        @Test
        @DisplayName("an int-RGB source keeps its own type and dimensions")
        void rgbKeepsOwnType() {
            BufferedImage source = new BufferedImage(5, 7, BufferedImage.TYPE_INT_RGB);
            BufferedImage shape = Pixels.sameShape(source);
            assertEquals(BufferedImage.TYPE_INT_RGB, shape.getType());
            assertEquals(5, shape.getWidth());
            assertEquals(7, shape.getHeight());
        }

        @Test
        @DisplayName("an int-ARGB source keeps TYPE_INT_ARGB")
        void argbKeepsOwnType() {
            BufferedImage source = new BufferedImage(5, 7, BufferedImage.TYPE_INT_ARGB);
            assertEquals(BufferedImage.TYPE_INT_ARGB, Pixels.sameShape(source).getType());
        }

        @Test
        @DisplayName("a non-packed source falls back to TYPE_INT_ARGB")
        void nonPackedFallsBackToIntArgb() {
            BufferedImage source = new BufferedImage(5, 7, BufferedImage.TYPE_3BYTE_BGR);
            BufferedImage shape = Pixels.sameShape(source);
            assertEquals(BufferedImage.TYPE_INT_ARGB, shape.getType());
            assertEquals(5, shape.getWidth());
            assertEquals(7, shape.getHeight());
        }
    }

    @Nested
    @DisplayName("data")
    class Data {

        @Test
        @DisplayName("returns the backing array shared with the image")
        void returnsBackingArray() {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            int[] pixels = Pixels.data(image);
            pixels[0] = 0xFF_ABCDEF;
            assertEquals(0xFF_ABCDEF, image.getRGB(0, 0));
        }

        @Test
        @DisplayName("a non-int-packed image is rejected")
        void nonPackedImageThrows() {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_3BYTE_BGR);
            assertThrows(PipelineException.class, () -> Pixels.data(image));
        }
    }

    @Nested
    @DisplayName("stride")
    class Stride {

        @Test
        @DisplayName("a subimage of a packed image uses the parent's scanline stride, not its own width")
        void packedSubimageUsesParentStride() {
            BufferedImage parent = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            BufferedImage sub = parent.getSubimage(2, 2, 5, 5);
            assertEquals(10, Pixels.stride(sub));
        }

        @Test
        @DisplayName("a whole packed image's stride equals its width")
        void wholePackedImageStrideEqualsWidth() {
            BufferedImage image = new BufferedImage(6, 4, BufferedImage.TYPE_INT_RGB);
            assertEquals(6, Pixels.stride(image));
        }

        @Test
        @DisplayName("a non-packed image falls back to its own width")
        void nonPackedFallsBackToWidth() {
            BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_3BYTE_BGR);
            assertEquals(20, Pixels.stride(image));
        }
    }

    @Nested
    @DisplayName("offset")
    class Offset {

        @Test
        @DisplayName("a whole packed image has offset 0")
        void wholePackedImageOffsetIsZero() {
            BufferedImage image = new BufferedImage(6, 4, BufferedImage.TYPE_INT_RGB);
            assertEquals(0, Pixels.offset(image));
        }

        @Test
        @DisplayName("a subimage's offset is its origin's index in the parent's backing array")
        void packedSubimageOffsetIsParentIndex() {
            BufferedImage parent = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            BufferedImage sub = parent.getSubimage(2, 2, 5, 5);
            assertEquals(22, Pixels.offset(sub));
        }

        @Test
        @DisplayName("a non-packed-but-int-backed raster falls back to originY * width + originX")
        void nonPackedIntBackedFallsBackToRowMajorFormula() {
            assertEquals(6, Pixels.offset(exoticSubimage()));
        }
    }

    @Nested
    @DisplayName("index")
    class Index {

        @Test
        @DisplayName("combines offset and stride: index(x,y) == offset + y*stride + x")
        void combinesOffsetAndStride() {
            BufferedImage parent = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            BufferedImage sub = parent.getSubimage(2, 2, 5, 5);
            assertEquals(22, Pixels.index(sub, 0, 0));
            assertEquals(33, Pixels.index(sub, 1, 1));
        }

        @Test
        @DisplayName("also composes correctly through the non-packed fallback formulas")
        void combinesThroughFallbackFormulas() {
            assertEquals(11, Pixels.index(exoticSubimage(), 1, 1));
        }
    }

    @Nested
    @DisplayName("clamp8(int)")
    class Clamp8Int {

        @Test
        @DisplayName("in-range values pass through unchanged")
        void inRangePassesThrough() {
            assertEquals(0, Pixels.clamp8(0));
            assertEquals(255, Pixels.clamp8(255));
            assertEquals(128, Pixels.clamp8(128));
        }

        @Test
        @DisplayName("values above 255 clamp to 255")
        void aboveRangeClampsToMax() {
            assertEquals(255, Pixels.clamp8(256));
        }

        @Test
        @DisplayName("negative values clamp to 0")
        void belowRangeClampsToZero() {
            assertEquals(0, Pixels.clamp8(-1));
        }
    }

    @Nested
    @DisplayName("clamp8(double)")
    class Clamp8Double {

        @Test
        @DisplayName("rounds to the nearest integer before clamping")
        void roundsBeforeClamping() {
            assertEquals(3, Pixels.clamp8(2.6));
            assertEquals(2, Pixels.clamp8(2.4));
        }

        @Test
        @DisplayName("still clamps out-of-range results after rounding")
        void stillClampsOutOfRange() {
            assertEquals(255, Pixels.clamp8(999.9));
            assertEquals(0, Pixels.clamp8(-0.9));
        }
    }

    @Nested
    @DisplayName("clampCoord")
    class ClampCoord {

        @Test
        @DisplayName("an in-range coordinate passes through unchanged")
        void inRangePassesThrough() {
            assertEquals(4, Pixels.clampCoord(4, 5));
        }

        @Test
        @DisplayName("a coordinate equal to the limit clamps to limit-1")
        void atLimitClampsToLastValidIndex() {
            assertEquals(4, Pixels.clampCoord(5, 5));
        }

        @Test
        @DisplayName("a negative coordinate clamps to 0")
        void negativeClampsToZero() {
            assertEquals(0, Pixels.clampCoord(-1, 5));
        }

        @Test
        @DisplayName("zero passes through unchanged")
        void zeroPassesThrough() {
            assertEquals(0, Pixels.clampCoord(0, 5));
        }
    }

    @Nested
    @DisplayName("channel accessors")
    class ChannelAccessors {

        private final int argb = 0x12_34_56_78;

        @Test
        @DisplayName("alpha() extracts the top byte")
        void alphaExtractsTopByte() {
            assertEquals(0x12, Pixels.alpha(argb));
        }

        @Test
        @DisplayName("red() extracts the second byte")
        void redExtractsSecondByte() {
            assertEquals(0x34, Pixels.red(argb));
        }

        @Test
        @DisplayName("green() extracts the third byte")
        void greenExtractsThirdByte() {
            assertEquals(0x56, Pixels.green(argb));
        }

        @Test
        @DisplayName("blue() extracts the low byte")
        void blueExtractsLowByte() {
            assertEquals(0x78, Pixels.blue(argb));
        }

        @Test
        @DisplayName("alpha() treats the top byte as unsigned")
        void alphaIsUnsigned() {
            assertEquals(0xFF, Pixels.alpha(0xFF_00_00_00));
        }
    }

    @Nested
    @DisplayName("pack")
    class Pack {

        @Test
        @DisplayName("packs four bytes into the expected ARGB layout")
        void packsIntoArgbLayout() {
            assertEquals(0x12345678, Pixels.pack(0x12, 0x34, 0x56, 0x78));
        }

        @Test
        @DisplayName("round-trips through the channel accessors")
        void roundTripsThroughAccessors() {
            int packed = Pixels.pack(0xAA, 0xBB, 0xCC, 0xDD);
            assertEquals(0xAA, Pixels.alpha(packed));
            assertEquals(0xBB, Pixels.red(packed));
            assertEquals(0xCC, Pixels.green(packed));
            assertEquals(0xDD, Pixels.blue(packed));
        }
    }

    /**
     * An 8x6 image whose raster is backed by a {@code DataBufferInt} but whose sample model is a
     * plain {@link ComponentSampleModel}, not a {@link java.awt.image.SinglePixelPackedSampleModel}.
     * This is the only way to reach {@code offset()}'s and {@code stride()}'s non-packed fallback
     * branch without hitting the {@code ClassCastException} a real byte-backed image (e.g.
     * {@code TYPE_3BYTE_BGR}) throws first at the unconditional {@code DataBufferInt} cast.
     */
    private static BufferedImage exoticSubimage() {
        int width = 8;
        int height = 6;
        ComponentSampleModel sampleModel =
                new ComponentSampleModel(DataBuffer.TYPE_INT, width, height, 1, width, new int[] {0});
        DataBufferInt dataBuffer = new DataBufferInt(width * height);
        WritableRaster raster = Raster.createWritableRaster(sampleModel, dataBuffer, new Point(0, 0));
        ColorModel colorModel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY),
                false, false, ColorModel.OPAQUE, DataBuffer.TYPE_INT);
        BufferedImage image = new BufferedImage(colorModel, raster, false, null);
        return image.getSubimage(2, 1, 4, 3);
    }
}
