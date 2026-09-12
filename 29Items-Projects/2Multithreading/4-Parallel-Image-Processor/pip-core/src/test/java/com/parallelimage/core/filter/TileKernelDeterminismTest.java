package com.parallelimage.core.filter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.fork.CancellationToken;
import com.parallelimage.core.fork.ForkJoinConfig;
import com.parallelimage.core.fork.TileProcessingAction;
import com.parallelimage.core.model.Tile;
import com.parallelimage.core.pipeline.TileKernel;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The single most important property in the codebase: <strong>output must not depend on how the image
 * was tiled.</strong>
 *
 * <p>If a kernel ever reads a neighbour's partially-written output instead of the immutable source, the
 * bug shows up as a faint grid of seams at tile boundaries — invisible in a unit test that only checks
 * one pixel, obvious in a byte-for-byte comparison against a sequential run. So that is what these
 * tests do: run each kernel whole-image on the calling thread, then again through the Level-2 tree at
 * an absurdly small threshold (forcing hundreds of tiles), and demand identical rasters.
 *
 * <p>A threshold of 64 pixels is deliberately far below anything production would use. Real batches
 * use ~65 536; here we want maximum tile-boundary pressure.
 */
class TileKernelDeterminismTest {

    private static final int WIDTH = 137;
    private static final int HEIGHT = 91;
    private static final long TINY_THRESHOLD = 64L;

    private ForkJoinPool pool;

    @AfterEach
    void shutdownPool() {
        if (pool != null) {
            ForkJoinConfig.shutdownGracefully(pool, 5, TimeUnit.SECONDS);
        }
    }

    static List<Supplier<TileKernel>> kernels() {
        return List.of(
                GrayscaleFilter::new,
                () -> new BoxBlurFilter(1),
                () -> new BoxBlurFilter(4),
                () -> new BoxBlurFilter(17),
                () -> new SharpenFilter(0.5d),
                () -> new SharpenFilter(2.0d));
    }

    @ParameterizedTest
    @MethodSource("kernels")
    @DisplayName("tiled output is byte-identical to sequential output")
    void tiledOutputMatchesSequential(Supplier<TileKernel> factory) {
        BufferedImage source = noise(WIDTH, HEIGHT, false);

        BufferedImage sequential = Pixels.sameShape(source);
        factory.get().apply(source, sequential, Tile.whole(WIDTH, HEIGHT));

        BufferedImage tiled = Pixels.sameShape(source);
        pool = ForkJoinConfig.newPool(4);
        pool.invoke(TileProcessingAction.forWholeImage(
                source, tiled, factory.get(), TINY_THRESHOLD, CancellationToken.NONE));

        assertArrayEquals(Pixels.data(sequential), Pixels.data(tiled),
                "kernel " + factory.get().name() + " is tile-order dependent: it must read its halo "
                        + "from the immutable source, never from the destination");
    }

    @ParameterizedTest
    @MethodSource("kernels")
    @DisplayName("running twice at different parallelism gives the same answer")
    void parallelismDoesNotChangeOutput(Supplier<TileKernel> factory) {
        BufferedImage source = noise(WIDTH, HEIGHT, true);

        BufferedImage withOne = runTiled(source, factory.get(), 1);
        BufferedImage withEight = runTiled(source, factory.get(), 8);

        assertArrayEquals(Pixels.data(withOne), Pixels.data(withEight));
    }

    @ParameterizedTest
    @MethodSource("kernels")
    @DisplayName("kernels never write outside their tile")
    void kernelsWriteOnlyInsideTheirTile(Supplier<TileKernel> factory) {
        BufferedImage source = noise(WIDTH, HEIGHT, false);
        BufferedImage target = Pixels.sameShape(source);
        int[] pixels = Pixels.data(target);
        java.util.Arrays.fill(pixels, 0x0BADF00D);

        Tile tile = new Tile(20, 15, 40, 30);
        factory.get().apply(source, target, tile);

        int stride = Pixels.stride(target);
        int offset = Pixels.offset(target);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                boolean inside = x >= tile.x() && x < tile.maxX() && y >= tile.y() && y < tile.maxY();
                int value = pixels[offset + y * stride + x];
                if (!inside) {
                    assertEquals(0x0BADF00D, value,
                            "pixel " + x + "," + y + " is outside the tile but was written");
                }
            }
        }
    }

    @Test
    @DisplayName("grayscale collapses the channels and leaves alpha alone")
    void grayscalePreservesAlpha() {
        BufferedImage source = new BufferedImage(4, 1, BufferedImage.TYPE_INT_ARGB);
        int[] in = Pixels.data(source);
        in[0] = 0x80_FF0000;
        in[1] = 0x40_00FF00;
        in[2] = 0xFF_0000FF;
        in[3] = 0x00_FFFFFF;

        BufferedImage target = Pixels.sameShape(source);
        new GrayscaleFilter().apply(source, target, Tile.whole(4, 1));
        int[] out = Pixels.data(target);

        for (int i = 0; i < 4; i++) {
            assertEquals(in[i] >>> 24, out[i] >>> 24, "alpha must be copied verbatim");
            int r = Pixels.red(out[i]);
            assertEquals(r, Pixels.green(out[i]), "channels must be equal after grayscale");
            assertEquals(r, Pixels.blue(out[i]));
        }
        // BT.709 weights: green contributes most, blue least.
        assertTrue(Pixels.red(out[1]) > Pixels.red(out[0]), "green must weigh more than red");
        assertTrue(Pixels.red(out[0]) > Pixels.red(out[2]), "red must weigh more than blue");
        assertEquals(0xFF, Pixels.red(out[3]), "white stays white");
    }

    @Test
    @DisplayName("a blur of a flat region is the same flat region — no edge darkening")
    void blurOfFlatRegionIsFlat() {
        BufferedImage source = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        int[] in = Pixels.data(source);
        java.util.Arrays.fill(in, 0xFF_7F7F7F);

        BufferedImage target = Pixels.sameShape(source);
        new BoxBlurFilter(3).apply(source, target, Tile.whole(32, 32));

        for (int value : Pixels.data(target)) {
            assertEquals(0x7F, Pixels.red(value),
                    "clamping the halo (not skipping it) is what keeps flat areas flat");
            assertEquals(0x7F, Pixels.green(value));
            assertEquals(0x7F, Pixels.blue(value));
        }
    }

    @Test
    @DisplayName("blur actually reduces local contrast")
    void blurReducesContrast() {
        BufferedImage source = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        int[] in = Pixels.data(source);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                in[y * 16 + x] = ((x + y) % 2 == 0) ? 0xFF_000000 : 0xFF_FFFFFF;
            }
        }
        BufferedImage target = Pixels.sameShape(source);
        new BoxBlurFilter(2).apply(source, target, Tile.whole(16, 16));

        int centre = Pixels.data(target)[8 * 16 + 8];
        int grey = Pixels.red(centre);
        assertTrue(grey > 0x40 && grey < 0xC0, "a checkerboard must blur towards mid grey, got " + grey);
    }

    @Test
    @DisplayName("sharpen with amount 0 is a copy; with amount > 0 it changes edges")
    void sharpenAmountBehaviour() {
        BufferedImage source = noise(32, 32, false);

        BufferedImage neutral = Pixels.sameShape(source);
        new SharpenFilter(0.0d).apply(source, neutral, Tile.whole(32, 32));
        assertArrayEquals(Pixels.data(source), Pixels.data(neutral),
                "amount 0 must be an exact identity, not an approximate one");

        BufferedImage sharpened = Pixels.sameShape(source);
        new SharpenFilter(1.5d).apply(source, sharpened, Tile.whole(32, 32));
        assertFalse(java.util.Arrays.equals(Pixels.data(source), Pixels.data(sharpened)),
                "a real sharpen must change something");
    }

    private BufferedImage runTiled(BufferedImage source, TileKernel kernel, int parallelism) {
        BufferedImage target = Pixels.sameShape(source);
        ForkJoinPool localPool = ForkJoinConfig.newPool(parallelism);
        try {
            localPool.invoke(TileProcessingAction.forWholeImage(
                    source, target, kernel, TINY_THRESHOLD, CancellationToken.NONE));
        } finally {
            ForkJoinConfig.shutdownGracefully(localPool, 5, TimeUnit.SECONDS);
        }
        return target;
    }

    /** Deterministic pseudo-random content: a fixed seed keeps failures reproducible. */
    private static BufferedImage noise(int width, int height, boolean alpha) {
        BufferedImage image = new BufferedImage(width, height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        int[] pixels = Pixels.data(image);
        Random random = new Random(20260818L);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (alpha ? random.nextInt() : 0xFF00_0000 | random.nextInt(0x0100_0000));
        }
        return image;
    }
}
