package com.parallelimage.core.spi;

import com.parallelimage.core.filter.Pixels;
import com.parallelimage.core.pipeline.ImageOperation.EnhanceMode;
import java.awt.image.BufferedImage;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * The always-available fallback: a pure-Java approximation that never fails.
 *
 * <h2>Why a no-op provider is worth writing</h2>
 * Without it, every call site would need a null check and a decision about what to do when
 * enhancement is unavailable — and those decisions would drift apart. With it, the pipeline can call
 * {@code enhancer.enhance(...)} unconditionally. This is the Null Object pattern earning its keep in
 * the one place where "the feature might not exist on this machine" is a normal, expected state
 * rather than a misconfiguration.
 *
 * <p>It is not <em>quite</em> a no-op: {@link EnhanceMode#CLAHE} is approximated with a global
 * contrast stretch, which is cheap, entirely in Java, and visibly better than returning the input
 * unchanged. {@link EnhanceMode#DENOISE} and {@link EnhanceMode#SUPER_RESOLUTION} have no honest
 * pure-Java equivalent at reasonable cost, so they pass through and log once at {@code DEBUG}.
 *
 * <p>Registered via {@code META-INF/services} in {@code pip-app} rather than here, so a unit test can
 * assert that discovery falls back correctly with an empty classpath.
 */
public final class PassthroughEnhancer implements ImageEnhancer {

    private static final Logger LOG = System.getLogger(PassthroughEnhancer.class.getName());

    @Override
    public BufferedImage enhance(BufferedImage source, EnhanceMode mode, double strength) {
        if (mode != EnhanceMode.CLAHE || strength <= 0.0d) {
            LOG.log(Level.DEBUG, () -> "no pure-Java implementation for " + mode + "; passing through");
            return source;
        }
        return contrastStretch(source, strength);
    }

    /**
     * Global histogram stretch on the luma channel, blended with the original by {@code strength}.
     *
     * <p>Single-threaded on purpose: this runs on a worker that is already one of N, and nesting a
     * third level of decomposition here would buy nothing.
     */
    private BufferedImage contrastStretch(BufferedImage source, double strength) {
        BufferedImage normalized = Pixels.normalize(source);
        int[] pixels = Pixels.data(normalized);
        int stride = Pixels.stride(normalized);
        int offset = Pixels.offset(normalized);
        int width = normalized.getWidth();
        int height = normalized.getHeight();

        // Pass 1: luma histogram.
        int[] histogram = new int[256];
        for (int y = 0; y < height; y++) {
            int row = offset + y * stride;
            for (int x = 0; x < width; x++) {
                int argb = pixels[row + x];
                int luma = (299 * ((argb >> 16) & 0xFF)
                        + 587 * ((argb >> 8) & 0xFF)
                        + 114 * (argb & 0xFF)) / 1000;
                histogram[luma]++;
            }
        }

        // Clip the extreme 0.5% at each end so a handful of blown highlights cannot defeat the
        // stretch -- the standard trick, and the reason this looks like CLAHE rather than nothing.
        long total = (long) width * height;
        long clip = Math.max(1L, total / 200L);
        int low = percentile(histogram, clip);
        int high = percentileFromTop(histogram, clip);
        if (high - low < 8) {
            return source; // already flat or already full-range; stretching would only add noise
        }

        double scale = 255.0d / (high - low);
        int[] lut = new int[256];
        for (int v = 0; v < 256; v++) {
            int stretched = Pixels.clamp8((v - low) * scale);
            lut[v] = Pixels.clamp8(v + (stretched - v) * strength);
        }

        BufferedImage out = Pixels.sameShape(normalized);
        int[] dst = Pixels.data(out);
        int dstStride = Pixels.stride(out);
        int dstOffset = Pixels.offset(out);
        for (int y = 0; y < height; y++) {
            int srcRow = offset + y * stride;
            int dstRow = dstOffset + y * dstStride;
            for (int x = 0; x < width; x++) {
                int argb = pixels[srcRow + x];
                dst[dstRow + x] = (argb & 0xFF00_0000)
                        | (lut[(argb >> 16) & 0xFF] << 16)
                        | (lut[(argb >> 8) & 0xFF] << 8)
                        | lut[argb & 0xFF];
            }
        }
        return out;
    }

    private static int percentile(int[] histogram, long count) {
        long seen = 0;
        for (int v = 0; v < histogram.length; v++) {
            seen += histogram[v];
            if (seen >= count) {
                return v;
            }
        }
        return 0;
    }

    private static int percentileFromTop(int[] histogram, long count) {
        long seen = 0;
        for (int v = histogram.length - 1; v >= 0; v--) {
            seen += histogram[v];
            if (seen >= count) {
                return v;
            }
        }
        return 255;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean supports(EnhanceMode mode) {
        return mode == EnhanceMode.CLAHE;
    }

    /** Lowest possible priority so any real native provider is preferred. */
    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }

    @Override
    public String describe() {
        return "passthrough (pure Java, no OpenCV)";
    }
}
