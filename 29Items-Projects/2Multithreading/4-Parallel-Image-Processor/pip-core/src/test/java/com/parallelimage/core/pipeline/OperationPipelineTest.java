package com.parallelimage.core.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.error.PipelineException;
import com.parallelimage.core.filter.Pixels;
import com.parallelimage.core.fork.CancellationToken;
import com.parallelimage.core.fork.ForkJoinConfig;
import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.pipeline.ImageOperation.EnhanceMode;
import com.parallelimage.core.spi.ImageEnhancer;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link OperationPipeline} tests.
 *
 * <p>Two things are worth testing here that the individual filter tests cannot cover: that the
 * <em>sequence</em> of stages composes correctly (each stage reads the previous stage's output, not the
 * original), and that the pipeline behaves the same whether or not it happens to be running on a
 * fork/join worker — because {@link OperationPipeline} deliberately takes two different code paths for
 * those two cases and the whole point is that they agree.
 */
class OperationPipelineTest {

    @Test
    @DisplayName("an empty pipeline returns the (normalized) source untouched")
    void emptyPipelineIsIdentity() {
        BufferedImage source = noise(24, 18);
        OperationPipeline pipeline = OperationPipeline.of();

        assertTrue(pipeline.isEmpty());
        assertSame(source, pipeline.execute(source),
                "an int-packed source needs no normalization, so no copy should be made");
    }

    @Test
    @DisplayName("a non-int-packed source is normalized before any stage sees it")
    void nonIntPackedSourceIsNormalized() {
        BufferedImage bgr = new BufferedImage(16, 16, BufferedImage.TYPE_3BYTE_BGR);

        BufferedImage result = OperationPipeline.of(new ImageOperation.Grayscale()).execute(bgr);

        assertTrue(Pixels.isIntPacked(result),
                "every kernel indexes a DataBufferInt; normalization must happen at the boundary");
        assertNotSame(bgr, result);
    }

    @Test
    @DisplayName("stages compose in order: resize then blur is not blur then resize")
    void stagesComposeInOrder() {
        BufferedImage source = noise(64, 64);

        BufferedImage resizeThenBlur = OperationPipeline.of(
                new ImageOperation.Resize(16, 16, true),
                new ImageOperation.BoxBlur(2)).execute(source);
        BufferedImage blurThenResize = OperationPipeline.of(
                new ImageOperation.BoxBlur(2),
                new ImageOperation.Resize(16, 16, true)).execute(source);

        assertEquals(16, resizeThenBlur.getWidth());
        assertEquals(16, blurThenResize.getWidth());
        assertFalse(java.util.Arrays.equals(Pixels.data(resizeThenBlur), Pixels.data(blurThenResize)),
                "if these matched, a stage would be reading the original rather than its predecessor");
    }

    @Test
    @DisplayName("output geometry is predictable without running the pipeline")
    void outputSizePredictsGeometry() {
        OperationPipeline pipeline = OperationPipeline.of(
                new ImageOperation.Grayscale(),
                new ImageOperation.Resize(100, 100, true),
                new ImageOperation.BoxBlur(1));

        assertArrayEquals(new int[] {100, 50}, pipeline.outputSize(400, 200));
        // And the prediction must agree with reality.
        BufferedImage actual = pipeline.execute(noise(400, 200));
        assertEquals(100, actual.getWidth());
        assertEquals(50, actual.getHeight());
    }

    @Test
    @DisplayName("a resize to the current size is a no-op that returns the same instance")
    void resizeToSameSizeSkipsWork() {
        BufferedImage source = noise(32, 32);
        BufferedImage result = OperationPipeline.of(new ImageOperation.Resize(32, 32, true))
                .execute(source);
        assertSame(source, result, "re-encoding an already-correct image is pure waste");
    }

    @Test
    @DisplayName("preserveAspectRatio=false stretches; =true fits inside the box")
    void aspectRatioHandling() {
        BufferedImage wide = noise(400, 100);

        BufferedImage stretched = OperationPipeline.of(new ImageOperation.Resize(50, 50, false))
                .execute(wide);
        assertEquals(50, stretched.getWidth());
        assertEquals(50, stretched.getHeight());

        BufferedImage fitted = OperationPipeline.of(new ImageOperation.Resize(50, 50, true))
                .execute(wide);
        assertEquals(50, fitted.getWidth());
        assertEquals(13, fitted.getHeight(), "100 * (50/400) = 12.5, rounded to 13");
    }

    @Test
    @DisplayName("an extreme panorama never rounds a dimension down to zero")
    void degenerateResizeIsFloored() {
        BufferedImage panorama = noise(2_000, 3);
        BufferedImage result = OperationPipeline.of(ImageOperation.Resize.boundingBox(40))
                .execute(panorama);
        assertEquals(40, result.getWidth());
        assertEquals(1, result.getHeight(), "BufferedImage rejects a zero dimension");
    }

    @Test
    @DisplayName("running on a fork/join worker gives the same pixels as running inline")
    void workerAndInlinePathsAgree() {
        BufferedImage source = noise(96, 71);
        List<ImageOperation> operations = List.of(
                new ImageOperation.Grayscale(),
                new ImageOperation.BoxBlur(3),
                new ImageOperation.Sharpen(1.25d));

        // Inline path: not on a worker, so OperationPipeline.tiled() runs the kernel whole-image.
        BufferedImage inline = pipelineWith(operations, 64L).execute(source);

        // Worker path: inside the pool, so each stage forks a Level-2 tree at a 64-pixel leaf size.
        ForkJoinPool pool = ForkJoinConfig.newPool(4);
        BufferedImage onWorker;
        try {
            onWorker = pool.submit(() -> pipelineWith(operations, 64L).execute(source)).join();
        } finally {
            ForkJoinConfig.shutdownGracefully(pool, 5, TimeUnit.SECONDS);
        }

        assertArrayEquals(Pixels.data(inline), Pixels.data(onWorker),
                "the inline fallback and the forked tree must be observationally identical");
    }

    @Test
    @DisplayName("a cancelled token stops the pipeline between stages")
    void cancellationStopsBetweenStages() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        OperationPipeline pipeline = OperationPipeline.from(
                ProcessingOptions.defaults().withOperations(List.of(new ImageOperation.Grayscale())),
                null, null, token);

        assertThrows(CancellationException.class, () -> pipeline.execute(noise(16, 16)));
    }

    @Test
    @DisplayName("a failing stage is reported as a PipelineException naming that stage")
    void stageFailureIsAttributed() {
        ImageEnhancer exploding = new ImageEnhancer() {
            @Override
            public BufferedImage enhance(BufferedImage source, EnhanceMode mode, double strength) {
                throw new IllegalStateException("native handle closed");
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String describe() {
                return "exploding-test-enhancer";
            }
        };
        OperationPipeline pipeline = OperationPipeline.from(
                ProcessingOptions.defaults().withOperations(
                        List.of(new ImageOperation.Enhance(EnhanceMode.CLAHE, 0.5d))),
                exploding, null, CancellationToken.NONE);

        PipelineException thrown =
                assertThrows(PipelineException.class, () -> pipeline.execute(noise(8, 8)));
        assertEquals("enhance:clahe", thrown.operationName(),
                "the operator needs to know which stage failed, not just that one did");
        assertTrue(thrown.getMessage().contains("native handle closed"));
    }

    @Test
    @DisplayName("the default enhancer is the passthrough, so Enhance degrades instead of failing")
    void missingEnhancerDegradesGracefully() {
        BufferedImage source = noise(16, 16);
        BufferedImage result = OperationPipeline.of(
                new ImageOperation.Enhance(EnhanceMode.SUPER_RESOLUTION, 1.0d)).execute(source);

        assertEquals(source.getWidth(), result.getWidth());
        assertEquals(source.getHeight(), result.getHeight());
        assertArrayEquals(Pixels.data(source), Pixels.data(result),
                "no OpenCV on this machine must mean 'unenhanced', not 'batch failed'");
    }

    @Test
    @DisplayName("a text watermark changes pixels; an overlay path is resolved through the loader")
    void watermarkUsesTheOverlayLoader() {
        BufferedImage source = flat(64, 64, 0xFF_202020);
        // WatermarkFilter mutates in place (it owns the buffer for the duration of the job), so the
        // "before" pixels have to be copied out first — comparing against source.getData() afterwards
        // would compare the array with itself and pass no matter what.
        int[] before = Pixels.data(source).clone();
        BufferedImage text = OperationPipeline.of(ImageOperation.Watermark.ofText("(c) 2026"))
                .execute(source);
        assertFalse(java.util.Arrays.equals(before, Pixels.data(text)),
                "a watermark that changes nothing is a watermark nobody can see");

        AtomicInteger loads = new AtomicInteger();
        Path overlayPath = Path.of("logo.png");
        BufferedImage overlay = flat(16, 16, 0xFF_FF0000);
        OperationPipeline pipeline = OperationPipeline.from(
                ProcessingOptions.defaults().withOperations(List.of(new ImageOperation.Watermark(
                        null, overlayPath, ImageOperation.Anchor.CENTER, 1.0d, 0))),
                null,
                path -> {
                    assertEquals(overlayPath, path);
                    loads.incrementAndGet();
                    return overlay;
                },
                CancellationToken.NONE);

        pipeline.execute(flat(64, 64, 0xFF_202020));
        assertEquals(1, loads.get(), "the loader must be consulted exactly once per image");
    }

    @Test
    @DisplayName("operations() is an unmodifiable copy — callers cannot mutate a shared pipeline")
    void operationsAreDefensivelyCopied() {
        OperationPipeline pipeline = OperationPipeline.of(new ImageOperation.Grayscale());
        assertThrows(UnsupportedOperationException.class,
                () -> pipeline.operations().add(new ImageOperation.BoxBlur(1)));
    }

    private static OperationPipeline pipelineWith(List<ImageOperation> operations, long threshold) {
        return OperationPipeline.from(
                ProcessingOptions.builder()
                        .operations(operations)
                        .tileThresholdPixels(threshold)
                        .build(),
                null, null, CancellationToken.NONE);
    }

    private static BufferedImage noise(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] pixels = Pixels.data(image);
        Random random = new Random(4_2026L);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xFF00_0000 | random.nextInt(0x0100_0000);
        }
        return image;
    }

    private static BufferedImage flat(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.util.Arrays.fill(Pixels.data(image), argb);
        return image;
    }
}
