package com.parallelimage.core.pipeline;

import com.parallelimage.core.error.PipelineException;
import com.parallelimage.core.filter.BoxBlurFilter;
import com.parallelimage.core.filter.GrayscaleFilter;
import com.parallelimage.core.filter.Pixels;
import com.parallelimage.core.filter.ResizeFilter;
import com.parallelimage.core.filter.SharpenFilter;
import com.parallelimage.core.filter.WatermarkFilter;
import com.parallelimage.core.fork.CancellationToken;
import com.parallelimage.core.fork.TileProcessingAction;
import com.parallelimage.core.model.ProcessingOptions;
import com.parallelimage.core.model.Tile;
import com.parallelimage.core.spi.ImageEnhancer;
import com.parallelimage.core.util.Preconditions;
import java.awt.image.BufferedImage;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ForkJoinTask;
import java.util.function.Function;

/**
 * Runs an ordered list of {@link ImageOperation}s over one image.
 *
 * <h2>Where the two levels of parallelism meet</h2>
 * This class is the hinge. It is invoked from a Level-1 batch leaf — i.e. already on a worker thread —
 * and for each <em>tileable</em> operation it forks a Level-2 {@link TileProcessingAction} tree into
 * the same pool. That is what lets a single 48-megapixel panorama saturate every core instead of
 * occupying one while the rest idle.
 *
 * <h2>Dispatch is an exhaustive switch, by design</h2>
 * {@link ImageOperation} is sealed and {@link #applyOne} has no {@code default} branch. Adding a new
 * operation therefore breaks the build here until it is implemented — which is the correct failure
 * mode. A {@code default -> image} branch would turn "we forgot to wire up the new filter" into
 * "the new filter silently does nothing", a bug that reaches users.
 *
 * <h2>Immutable-source discipline</h2>
 * Each stage reads a source buffer and writes a fresh destination buffer, then the destination becomes
 * the next stage's source. That doubles peak memory for one image (~2 buffers, not N) and in return
 * makes every tileable stage race-free without a single lock: no worker ever writes to a buffer
 * another worker is reading. Whole-image stages that own their buffer exclusively
 * ({@link WatermarkFilter}) are allowed to mutate in place.
 *
 * <p><strong>Effectively immutable and thread-safe.</strong> One instance can be shared across a
 * batch; it holds only configuration.
 */
public final class OperationPipeline {

    private static final Logger LOG = System.getLogger(OperationPipeline.class.getName());

    private final List<ImageOperation> operations;
    private final long tileThresholdPixels;
    private final ImageEnhancer enhancer;
    private final Function<Path, BufferedImage> overlayLoader;
    private final CancellationToken token;

    private OperationPipeline(List<ImageOperation> operations, long tileThresholdPixels,
            ImageEnhancer enhancer, Function<Path, BufferedImage> overlayLoader,
            CancellationToken token) {
        this.operations = List.copyOf(operations);
        this.tileThresholdPixels = tileThresholdPixels;
        this.enhancer = enhancer;
        this.overlayLoader = overlayLoader;
        this.token = token;
    }

    /**
     * Builds a pipeline from validated options, substituting defaults for the optional collaborators.
     *
     * @param options       supplies the operation list and the Level-2 leaf size
     * @param enhancer      resolved once at startup by {@link ImageEnhancer#discover()}
     * @param overlayLoader decodes watermark overlays; should be caching, since the same PNG is used
     *                      by every image in a batch. {@code null} disables image overlays.
     * @param token         cooperative cancellation for the batch
     */
    public static OperationPipeline from(ProcessingOptions options, ImageEnhancer enhancer,
            Function<Path, BufferedImage> overlayLoader, CancellationToken token) {
        Preconditions.requireNonNull(options, "options");
        return new OperationPipeline(
                options.operations(),
                options.tileThresholdPixels(),
                enhancer == null ? new com.parallelimage.core.spi.PassthroughEnhancer() : enhancer,
                overlayLoader,
                token == null ? CancellationToken.NONE : token);
    }

    /** Test/CLI convenience: defaults for everything except the operations. */
    public static OperationPipeline of(ImageOperation... operations) {
        return from(ProcessingOptions.defaults().withOperations(List.of(operations)),
                null, null, CancellationToken.NONE);
    }

    /**
     * Applies every operation in order.
     *
     * @param source input, normalized to an int-packed type by the caller; never modified
     * @return the final image; may be {@code source} itself when the pipeline is empty
     * @throws PipelineException if a stage fails; the operation name is attached
     */
    public BufferedImage execute(BufferedImage source) {
        Preconditions.requireNonNull(source, "source");
        BufferedImage current = Pixels.normalize(source);

        for (ImageOperation operation : operations) {
            token.throwIfCancelled();
            try {
                current = applyOne(current, operation);
            } catch (PipelineException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new PipelineException(operation.name(), e.getMessage(), e);
            } catch (OutOfMemoryError e) {
                // Rethrown unchanged after annotating: this is a sizing problem (too many large
                // images in flight), not a bad input file, and the operator needs to see it as such.
                BufferedImage failed = current;
                LOG.log(Level.ERROR, () -> "out of memory during '" + operation.name()
                        + "' on a " + failed.getWidth() + "x" + failed.getHeight() + " image");
                throw e;
            }
        }
        return current;
    }

    /** Exhaustive dispatch over the sealed hierarchy. Do not add a {@code default} branch. */
    private BufferedImage applyOne(BufferedImage image, ImageOperation operation) {
        return switch (operation) {
            case ImageOperation.Resize resize -> ResizeFilter.apply(image, resize);
            case ImageOperation.Grayscale ignored -> tiled(image, new GrayscaleFilter());
            case ImageOperation.BoxBlur blur -> tiled(image, new BoxBlurFilter(blur.radius(), token));
            case ImageOperation.Sharpen sharpen -> tiled(image, new SharpenFilter(sharpen.amount()));
            case ImageOperation.Watermark watermark ->
                    WatermarkFilter.apply(image, watermark, overlay(watermark));
            case ImageOperation.Enhance enhance ->
                    enhancer.enhance(image, enhance.mode(), enhance.strength());
        };
    }

    /**
     * Runs a tileable kernel over the whole image via the Level-2 fork/join tree.
     *
     * <p>The {@link ForkJoinTask#inForkJoinPool()} check is not cosmetic. A {@code fork()} from a
     * thread that is <em>not</em> a fork/join worker silently submits to
     * {@code ForkJoinPool.commonPool()} — the pool this application deliberately never uses, because
     * it is shared with parallel streams and every library in the JVM. Running the kernel inline
     * instead keeps a CLI invocation or a unit test on the calling thread, correct and sequential,
     * with no surprise pool involvement (TECH-NOTES §3.6 A5).
     */
    private BufferedImage tiled(BufferedImage image, TileKernel kernel) {
        BufferedImage target = Pixels.sameShape(image);
        if (ForkJoinTask.inForkJoinPool()) {
            TileProcessingAction.forWholeImage(image, target, kernel, tileThresholdPixels, token)
                    .invoke();
        } else {
            LOG.log(Level.DEBUG, () -> "not on a fork/join worker; running '" + kernel.name()
                    + "' inline rather than on the common pool");
            kernel.apply(image, target, Tile.whole(image.getWidth(), image.getHeight()));
        }
        return target;
    }

    private BufferedImage overlay(ImageOperation.Watermark watermark) {
        if (watermark.overlay() == null || overlayLoader == null) {
            return null;
        }
        return overlayLoader.apply(watermark.overlay());
    }

    /** Output geometry after the pipeline, for pre-allocating and for progress reporting. */
    public int[] outputSize(int sourceWidth, int sourceHeight) {
        int width = sourceWidth;
        int height = sourceHeight;
        for (ImageOperation operation : operations) {
            if (operation instanceof ImageOperation.Resize resize) {
                int[] size = ResizeFilter.targetSize(width, height, resize);
                width = size[0];
                height = size[1];
            }
        }
        return new int[] {width, height};
    }

    public List<ImageOperation> operations() {
        return operations;
    }

    public boolean isEmpty() {
        return operations.isEmpty();
    }

    @Override
    public String toString() {
        return "OperationPipeline" + operations.stream().map(ImageOperation::name).toList();
    }
}
