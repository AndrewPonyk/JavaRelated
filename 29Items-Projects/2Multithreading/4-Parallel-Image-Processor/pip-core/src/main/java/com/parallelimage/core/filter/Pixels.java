package com.parallelimage.core.filter;

import com.parallelimage.core.error.PipelineException;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;

/**
 * Low-level pixel access helpers shared by every {@link com.parallelimage.core.pipeline.TileKernel}.
 *
 * <h2>Why not {@code getRGB} / {@code setRGB}</h2>
 * Both go through the colour-model machinery on every call: bounds checks, a
 * {@code ColorModel.getDataElements} dispatch, and — for non-int rasters — a colour conversion. On a
 * 48-megapixel image that is two orders of magnitude of overhead over touching the backing
 * {@code int[]} directly. Every kernel here therefore grabs the array once and indexes it
 * arithmetically (TECH-NOTES §3.6 E4).
 *
 * <h2>Thread-safety</h2>
 * Handing out the backing array of a shared destination image sounds alarming, and would be, except
 * that the tile contract guarantees each worker writes a <em>disjoint</em> index range. Disjoint
 * writes to distinct elements of the same array are safe under the Java Memory Model: element writes
 * do not interfere, and the {@code join()} in
 * {@link com.parallelimage.core.fork.TileProcessingAction} provides the happens-before edge that
 * publishes them all to the reader.
 *
 * <p>Word tearing is not a concern — the JLS forbids it for all array types.
 */
public final class Pixels {

    private Pixels() {
        throw new AssertionError("no instances");
    }

    /** Types whose backing buffer is a single {@code int} per pixel. */
    public static boolean isIntPacked(BufferedImage image) {
        return image.getType() == BufferedImage.TYPE_INT_RGB
                || image.getType() == BufferedImage.TYPE_INT_ARGB;
    }

    /**
     * Converts to {@code TYPE_INT_RGB}/{@code TYPE_INT_ARGB} when needed.
     *
     * <p>Called once per image at load time so no kernel ever has to handle the twenty-odd other
     * {@code BufferedImage} layouts ImageIO can hand back (indexed GIFs, 8-bit greyscale PNGs,
     * {@code TYPE_3BYTE_BGR} JPEGs, …). Returns the argument unchanged when it is already usable, so
     * the common path costs nothing.
     */
    public static BufferedImage normalize(BufferedImage source) {
        if (isIntPacked(source)) {
            return source;
        }
        int type = source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), type);
        java.awt.Graphics2D g = converted.createGraphics();
        try {
            g.drawImage(source, 0, 0, null);
        } finally {
            // Graphics2D holds native resources; leaking them shows up as a slow OOM, not a crash.
            g.dispose();
        }
        return converted;
    }

    /** Allocates an empty destination with the same geometry and layout as {@code source}. */
    public static BufferedImage sameShape(BufferedImage source) {
        return new BufferedImage(source.getWidth(), source.getHeight(),
                isIntPacked(source) ? source.getType() : BufferedImage.TYPE_INT_ARGB);
    }

    /**
     * Returns the backing {@code int[]}.
     *
     * @throws PipelineException if the image is not int-packed — a programming error, since
     *         {@link #normalize(BufferedImage)} runs at load time
     */
    public static int[] data(BufferedImage image) {
        if (!isIntPacked(image)) {
            throw new PipelineException("pixels",
                    "image was not normalized to an int-packed type (was type " + image.getType()
                            + "); call Pixels.normalize() at load time");
        }
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    /**
     * Elements between the start of one row and the start of the next.
     *
     * <p>Equal to the width for images we allocate ourselves, but <em>not</em> for a
     * {@code getSubimage()} view, which shares its parent's wider buffer. Kernels must index with
     * this rather than assuming {@code y * width}.
     */
    public static int stride(BufferedImage image) {
        WritableRaster raster = image.getRaster();
        if (raster.getSampleModel() instanceof SinglePixelPackedSampleModel packed) {
            return packed.getScanlineStride();
        }
        return image.getWidth();
    }

    /** Index of pixel (0,0) within the backing array; non-zero for subimage views. */
    public static int offset(BufferedImage image) {
        WritableRaster raster = image.getRaster();
        int dataOffset = ((DataBufferInt) raster.getDataBuffer()).getOffset();
        // A subimage's raster is translated relative to the shared sample model; undo that to get
        // the sample-model coordinates of the child's own origin.
        int originX = -raster.getSampleModelTranslateX();
        int originY = -raster.getSampleModelTranslateY();
        if (raster.getSampleModel() instanceof SinglePixelPackedSampleModel packed) {
            return dataOffset + packed.getOffset(originX, originY);
        }
        return dataOffset + originY * image.getWidth() + originX;
    }

    /**
     * Index of pixel {@code (x, y)} in the backing array of {@code image}.
     *
     * <p>Convenience for setup code and tests only. Inside a kernel's inner loop, hoist
     * {@link #offset(BufferedImage)} and {@link #stride(BufferedImage)} into locals and do the
     * arithmetic there — calling this per pixel reintroduces exactly the overhead the direct array
     * access was meant to remove.
     */
    public static int index(BufferedImage image, int x, int y) {
        return offset(image) + y * stride(image) + x;
    }

    /** Clamps an 8-bit channel value computed in wider arithmetic. */
    public static int clamp8(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }

    /** Clamps a floating-point channel value to a valid 8-bit integer. */
    public static int clamp8(double value) {
        return clamp8((int) Math.round(value));
    }

    /** Clamps a coordinate into {@code [0, limit)} — used for convolution halo edge handling. */
    public static int clampCoord(int value, int limit) {
        return value < 0 ? 0 : Math.min(value, limit - 1);
    }

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    public static int pack(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
